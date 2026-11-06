package com.queueflow.it;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration over real PostgreSQL (queueflow_test on :5433).
 * Covers auth/roles, sequencing, daily reset, priority pattern, state rules and concurrency.
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowIT {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    final ObjectMapper mapper = new ObjectMapper();

    static String adminSession, att1Session, att2Session;
    static Long queueAId, queueBId, counter1Id, counter2Id, att2UserId;

    ResponseEntity<JsonNode> post(String path, Object body, String session) {
        return exchange(HttpMethod.POST, path, body, session);
    }

    ResponseEntity<JsonNode> exchange(HttpMethod method, String path, Object body, String session) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (session != null) h.add(HttpHeaders.COOKIE, session);
        return rest.exchange(path, method, new HttpEntity<>(body, h), JsonNode.class);
    }

    JsonNode body(ResponseEntity<JsonNode> r) { return r.getBody(); }

    String sessionCookie(ResponseEntity<?> response) {
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie, "authentication response must set a session cookie");
        assertTrue(setCookie.contains("HttpOnly"), "session cookie must be HttpOnly");
        return setCookie.split(";", 2)[0];
    }

    @Test
    @Order(0)
    void sessionLookupWithoutValidCookieReturnsUnauthorized() {
        var anonymous = exchange(HttpMethod.GET, "/api/auth/me", null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());
        assertEquals(401, body(anonymous).path("status").asInt());
        assertEquals("Não autenticado.", body(anonymous).path("message").asText());

        var invalid = exchange(HttpMethod.GET, "/api/auth/me", null, "qf_session=invalid");
        assertEquals(HttpStatus.UNAUTHORIZED, invalid.getStatusCode());
    }

    /** Login com senha provisória + troca obrigatória; retorna a sessão já definitiva. */
    String provisionAttendant(String email, String tempPassword, String newPassword) {
        var login = post("/api/auth/login", Map.of("email", email, "password", tempPassword), null);
        assertEquals(HttpStatus.OK, login.getStatusCode());
        assertTrue(body(login).path("mustChangePassword").asBoolean(),
                "atendente recém-criado pelo admin deve começar com troca obrigatória");
        var blocked = exchange(HttpMethod.GET, "/api/tickets/state?counterId=1", null, sessionCookie(login));
        assertEquals(HttpStatus.FORBIDDEN, blocked.getStatusCode(), "rotas de operação bloqueadas até a troca");
        var changed = post("/api/auth/change-password",
                Map.of("currentPassword", tempPassword, "newPassword", newPassword), sessionCookie(login));
        assertEquals(HttpStatus.OK, changed.getStatusCode());
        return sessionCookie(changed);
    }

    @Test
    @Order(1)
    void firstUserIsAdmin_publicRegisterCloses_attendantsProvisionedByAdmin() {
        var reg1 = post("/api/auth/register",
                Map.of("name", "Admin", "email", "admin@test.io", "password", "Secret1!23456"), null);
        assertEquals(HttpStatus.OK, reg1.getStatusCode());
        assertEquals("ADMIN", body(reg1).path("role").asText());
        assertFalse(body(reg1).has("token"));
        adminSession = sessionCookie(reg1);

        // com um usuário existente, o cadastro público fecha (antes criava atendente livremente)
        var reg2 = post("/api/auth/register",
                Map.of("name", "Tardio", "email", "late@test.io", "password", "Secret1!23456"), null);
        assertEquals(HttpStatus.FORBIDDEN, reg2.getStatusCode(), "register must be bootstrap-only");

        // atendentes são provisionados pelo admin, com senha provisória e troca obrigatória
        var mk1 = post("/api/users",
                Map.of("name", "Atendente 1", "email", "att1@test.io", "password", "Secret1!23456"), adminSession);
        assertEquals(HttpStatus.OK, mk1.getStatusCode());
        assertEquals("ATTENDANT", body(mk1).path("role").asText());
        assertTrue(body(mk1).path("mustChangePassword").asBoolean());

        var mk2 = post("/api/users",
                Map.of("name", "Atendente 2", "email", "att2@test.io", "password", "Secret1!23456"), adminSession);
        assertEquals(HttpStatus.OK, mk2.getStatusCode());
        att2UserId = body(mk2).path("id").asLong();

        att1Session = provisionAttendant("att1@test.io", "Secret1!23456", "NovaSenhaAtt1!");
        att2Session = provisionAttendant("att2@test.io", "Secret1!23456", "NovaSenhaAtt2!");

        var login = post("/api/auth/login", Map.of("email", "admin@test.io", "password", "Secret1!23456"), null);
        assertEquals(HttpStatus.OK, login.getStatusCode());
        assertFalse(body(login).has("token"));
        assertNotNull(sessionCookie(login));

        var me = exchange(HttpMethod.GET, "/api/auth/me", null, adminSession);
        assertEquals(HttpStatus.OK, me.getStatusCode());
        assertEquals("ADMIN", body(me).path("role").asText());

        var attendantMe = exchange(HttpMethod.GET, "/api/auth/me", null, att1Session);
        assertEquals(HttpStatus.OK, attendantMe.getStatusCode());
        assertEquals("ATTENDANT", body(attendantMe).path("role").asText());

        var bad = post("/api/auth/login", Map.of("email", "admin@test.io", "password", "wrong"), null);
        assertEquals(HttpStatus.UNAUTHORIZED, bad.getStatusCode());
    }

    @Test
    @Order(2)
    void adminEndpoints_rolesAndDuplicates() {
        // Rotas de gestão devem negar acesso sem sessão e com papel ATTENDANT.
        for (String endpoint : List.of("/api/queues", "/api/users", "/api/history",
                "/api/dashboard/today", "/api/settings")) {
            assertEquals(HttpStatus.UNAUTHORIZED,
                    exchange(HttpMethod.GET, endpoint, null, null).getStatusCode(),
                    "anonymous access to " + endpoint);
            assertEquals(HttpStatus.FORBIDDEN,
                    exchange(HttpMethod.GET, endpoint, null, att1Session).getStatusCode(),
                    "attendant access to " + endpoint);
        }
        assertEquals(HttpStatus.FORBIDDEN,
                post("/api/queues", Map.of("name", "X", "prefix", "X"), att1Session).getStatusCode());

        var qA = post("/api/queues", Map.of("name", "Fila A", "prefix", "A"), adminSession);
        assertEquals(HttpStatus.OK, qA.getStatusCode());
        queueAId = body(qA).path("id").asLong();

        var c1 = post("/api/counters", Map.of("name", "Guichê 01"), adminSession);
        var c2 = post("/api/counters", Map.of("name", "Guichê 02"), adminSession);
        assertEquals(HttpStatus.OK, c1.getStatusCode());
        counter1Id = body(c1).path("id").asLong();
        counter2Id = body(c2).path("id").asLong();

        assertEquals(HttpStatus.CONFLICT, post("/api/queues", Map.of("name", "Outra", "prefix", "A"), adminSession).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, post("/api/counters", Map.of("name", "Guichê 01"), adminSession).getStatusCode());

        assertEquals(HttpStatus.OK, post("/api/counters/" + counter1Id + "/claim", Map.of(), att1Session).getStatusCode());
        assertEquals(HttpStatus.OK, post("/api/counters/" + counter2Id + "/claim", Map.of(), att2Session).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, post("/api/counters/" + counter1Id + "/claim", Map.of(), att2Session).getStatusCode());
    }

    @Test
    @Order(3)
    void sequentialIssuance_andPriorityPattern() {
        for (int i = 0; i < 4; i++)
            post("/api/public/tickets", Map.of("queueId", queueAId, "priorityType", "NORMAL"), null);
        for (int i = 0; i < 2; i++)
            post("/api/public/tickets", Map.of("queueId", queueAId, "priorityType", "PRIORITY"), null);

        List<String> order = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            var called = post("/api/tickets/call-next", Map.of("counterId", counter1Id), att1Session);
            assertEquals(HttpStatus.OK, called.getStatusCode());
            order.add(body(called).path("displayCode").asText());
            assertEquals(HttpStatus.OK, post("/api/tickets/start", Map.of("counterId", counter1Id), att1Session).getStatusCode());
            assertEquals(HttpStatus.OK, post("/api/tickets/finish", Map.of("counterId", counter1Id), att1Session).getStatusCode());
        }
        assertEquals(List.of("A001", "A002", "P001", "A003", "A004", "P002"), order);
    }

    @Test
    @Order(4)
    void stateRules_andNormalFallback() {
        for (int i = 0; i < 2; i++)
            post("/api/public/tickets", Map.of("queueId", queueAId, "priorityType", "NORMAL"), null);

        var called = post("/api/tickets/call-next", Map.of("counterId", counter1Id), att1Session);
        assertEquals(HttpStatus.OK, called.getStatusCode());
        assertTrue(body(called).path("displayCode").asText().startsWith("A"));

        assertEquals(HttpStatus.CONFLICT, post("/api/tickets/finish", Map.of("counterId", counter1Id), att1Session).getStatusCode());
        assertEquals(HttpStatus.OK, post("/api/tickets/recall", Map.of("counterId", counter1Id), att1Session).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, post("/api/tickets/call-next", Map.of("counterId", counter1Id), att1Session).getStatusCode());
        assertEquals(HttpStatus.OK, post("/api/tickets/absent", Map.of("counterId", counter1Id), att1Session).getStatusCode());
        var state = exchange(HttpMethod.GET, "/api/tickets/state?counterId=" + counter1Id, null, att1Session);
        assertTrue(state.getBody().path("current").isNull());
    }

    @Test
    @Order(5)
    void dailySequenceReset() {
        var qB = post("/api/queues", Map.of("name", "Fila B", "prefix", "B"), adminSession);
        queueBId = body(qB).path("id").asLong();

        jdbc.update("INSERT INTO daily_sequences(queue_id, day, last_number) VALUES (?, ?, 99)",
                queueBId, LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).minusDays(1));
        var firstToday = post("/api/public/tickets", Map.of("queueId", queueBId, "priorityType", "NORMAL"), null);
        assertEquals("B001", body(firstToday).path("displayCode").asText());

        jdbc.update("UPDATE daily_sequences SET last_number = 5 WHERE queue_id = ? AND day = ?",
                queueBId, LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")));
        var nextToday = post("/api/public/tickets", Map.of("queueId", queueBId, "priorityType", "NORMAL"), null);
        assertEquals("B006", body(nextToday).path("displayCode").asText());
    }

    @Test
    @Order(6)
    void concurrentCallNext_neverDuplicatesTicket() throws Exception {
        for (int i = 0; i < 10; i++)
            post("/api/public/tickets", Map.of("queueId", queueAId, "priorityType", "NORMAL"), null);

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<AtomicReference<String>> codes = List.of(new AtomicReference<String>(), new AtomicReference<String>());
        var sessions = List.of(att1Session, att2Session);
        var counters = List.of(counter1Id, counter2Id);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    var r = post("/api/tickets/call-next", Map.of("counterId", counters.get(idx)), sessions.get(idx));
                    codes.get(idx).set(r.getStatusCode().is2xxSuccessful() ? body(r).path("displayCode").asText() : "ERR:" + r.getStatusCode());
                } catch (Exception e) {
                    codes.get(idx).set("EX:" + e.getMessage());
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "concurrent calls timed out");
        pool.shutdown();

        String c1 = codes.get(0).get(), c2 = codes.get(1).get();
        assertFalse(c1.startsWith("ERR") || c1.startsWith("EX"), "attendant1 failed: " + c1);
        assertFalse(c2.startsWith("ERR") || c2.startsWith("EX"), "attendant2 failed: " + c2);
        assertNotEquals(c1, c2, "two counters received the SAME ticket");

        Integer dup = jdbc.queryForObject(
                "SELECT count(*) FROM (SELECT display_code FROM tickets WHERE status='CALLED' GROUP BY display_code HAVING count(*) > 1) d",
                Integer.class);
        assertEquals(0, dup, "duplicate CALLED display_code found");
    }

    @Test
    @Order(7)
    void deactivatedUserCannotLogin_orUseOldSession() {
        var upd = exchange(HttpMethod.PUT, "/api/users/" + att2UserId, Map.of("active", false), adminSession);
        assertEquals(HttpStatus.OK, upd.getStatusCode());

        var login = post("/api/auth/login", Map.of("email", "att2@test.io", "password", "NovaSenhaAtt2!"), null);
        assertEquals(HttpStatus.FORBIDDEN, login.getStatusCode());

        var blocked = post("/api/tickets/call-next", Map.of("counterId", counter2Id), att2Session);
        assertEquals(HttpStatus.UNAUTHORIZED, blocked.getStatusCode());

        exchange(HttpMethod.PUT, "/api/users/" + att2UserId, Map.of("active", true), adminSession);
    }

    @Test
    @Order(8)
    void logoutClearsSessionCookie() {
        var logout = post("/api/auth/logout", null, att1Session);
        assertEquals(HttpStatus.NO_CONTENT, logout.getStatusCode());
        String setCookie = logout.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.startsWith("qf_session="));
        assertTrue(setCookie.contains("Max-Age=0"));
    }

    @Test
    @Order(9)
    void historyDashboardAndPublicEndpoints() {
        var hist = exchange(HttpMethod.GET, "/api/history", null, adminSession);
        assertEquals(HttpStatus.OK, hist.getStatusCode());
        assertTrue(hist.getBody().path("totalElements").asInt() > 0);
        JsonNode firstRow = hist.getBody().path("content").get(0);
        assertNotNull(firstRow.path("waitMinutes"));

        var dash = exchange(HttpMethod.GET, "/api/dashboard/today", null, adminSession);
        assertEquals(HttpStatus.OK, dash.getStatusCode());
        assertTrue(dash.getBody().path("issuedToday").asInt() >= 14);
        assertTrue(dash.getBody().path("finishedToday").asInt() > 0);
        // volume por fila vem de agregação no banco, com a fila mais movimentada primeiro
        var byQueue = dash.getBody().path("byQueue");
        assertTrue(byQueue.size() >= 1, "byQueue deve refletir as emissões do dia");
        assertEquals("Fila A", byQueue.get(0).path("queueName").asText());
        assertTrue(byQueue.get(0).path("issued").asInt() >= 14);

        var badPage = exchange(HttpMethod.GET, "/api/history?page=-1", null, adminSession);
        assertEquals(HttpStatus.BAD_REQUEST, badPage.getStatusCode(), "página negativa deve ser 400, não 500");
        var badSize = exchange(HttpMethod.GET, "/api/history?size=0", null, adminSession);
        assertEquals(HttpStatus.BAD_REQUEST, badSize.getStatusCode());

        var display = exchange(HttpMethod.GET, "/api/public/display", null, null);
        assertEquals(HttpStatus.OK, display.getStatusCode());
        assertNotNull(display.getBody().path("highlight"));

        var publicQueues = exchange(HttpMethod.GET, "/api/public/queues", null, null);
        assertTrue(publicQueues.getBody().size() >= 2);

        var settings = exchange(HttpMethod.GET, "/api/settings/priority", null, adminSession);
        assertEquals(2, settings.getBody().path("normalsBeforePriority").asInt());
    }

    @Test
    @Order(10)
    void issueDayFunctionMatchesBusinessZone() {
        // 2026-11-07 02:30 UTC = 2026-11-06 23:30 em America/Sao_Paulo: a função do
        // índice único precisa concordar com o BusinessClock, senão a emissão quebra
        // na virada do dia por violação de unicidade.
        java.sql.Date d = jdbc.queryForObject(
                "SELECT ticket_issue_day('2026-11-07 02:30:00+00'::timestamptz)", java.sql.Date.class);
        assertEquals(LocalDate.of(2026, 11, 6), d.toLocalDate());
    }

    @Test
    @Order(11)
    void cancelRecordsActingAttendant() {
        post("/api/public/tickets", Map.of("queueId", queueAId, "priorityType", "NORMAL"), null);
        var hist = exchange(HttpMethod.GET, "/api/history?status=WAITING&size=1", null, adminSession);
        long id = hist.getBody().path("content").get(0).path("ticket").path("id").asLong();

        var cancel = post("/api/tickets/" + id + "/cancel", null, att1Session);
        assertEquals(HttpStatus.OK, cancel.getStatusCode());
        assertEquals("CANCELLED", body(cancel).path("status").asText());

        Integer recorded = jdbc.queryForObject(
                "SELECT count(*) FROM ticket_events WHERE ticket_id = ? AND type = 'CANCELLED' AND attendant_id IS NOT NULL",
                Integer.class, id);
        assertEquals(1, recorded, "cancelamento deve registrar o atendente no evento");
    }
}

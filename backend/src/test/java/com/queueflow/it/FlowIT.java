package com.queueflow.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
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
    @Order(1)
    void firstUserIsAdmin_secondIsAttendant_loginWorks() {
        var reg1 = post("/api/auth/register",
                Map.of("name", "Admin", "email", "admin@test.io", "password", "Secret1!"), null);
        assertEquals(HttpStatus.OK, reg1.getStatusCode());
        assertEquals("ADMIN", body(reg1).path("role").asText());
        assertFalse(body(reg1).has("token"));
        adminSession = sessionCookie(reg1);

        var reg2 = post("/api/auth/register",
                Map.of("name", "Atendente 1", "email", "att1@test.io", "password", "Secret1!"), null);
        assertEquals("ATTENDANT", body(reg2).path("role").asText());
        att1Session = sessionCookie(reg2);

        var reg3 = post("/api/auth/register",
                Map.of("name", "Atendente 2", "email", "att2@test.io", "password", "Secret1!"), null);
        att2Session = sessionCookie(reg3);
        att2UserId = body(reg3).path("id").asLong();

        var login = post("/api/auth/login", Map.of("email", "admin@test.io", "password", "Secret1!"), null);
        assertEquals(HttpStatus.OK, login.getStatusCode());
        assertFalse(body(login).has("token"));
        assertNotNull(sessionCookie(login));

        var me = exchange(HttpMethod.GET, "/api/auth/me", null, adminSession);
        assertEquals(HttpStatus.OK, me.getStatusCode());
        assertEquals("ADMIN", body(me).path("role").asText());

        var bad = post("/api/auth/login", Map.of("email", "admin@test.io", "password", "wrong"), null);
        assertEquals(HttpStatus.UNAUTHORIZED, bad.getStatusCode());
    }

    @Test
    @Order(2)
    void adminEndpoints_rolesAndDuplicates() {
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
                queueBId, LocalDate.now().minusDays(1));
        var firstToday = post("/api/public/tickets", Map.of("queueId", queueBId, "priorityType", "NORMAL"), null);
        assertEquals("B001", body(firstToday).path("displayCode").asText());

        jdbc.update("UPDATE daily_sequences SET last_number = 5 WHERE queue_id = ? AND day = ?", queueBId, LocalDate.now());
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

        var login = post("/api/auth/login", Map.of("email", "att2@test.io", "password", "Secret1!"), null);
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

        var display = exchange(HttpMethod.GET, "/api/public/display", null, null);
        assertEquals(HttpStatus.OK, display.getStatusCode());
        assertNotNull(display.getBody().path("highlight"));

        var publicQueues = exchange(HttpMethod.GET, "/api/public/queues", null, null);
        assertTrue(publicQueues.getBody().size() >= 2);

        var settings = exchange(HttpMethod.GET, "/api/settings/priority", null, adminSession);
        assertEquals(2, settings.getBody().path("normalsBeforePriority").asInt());
    }
}

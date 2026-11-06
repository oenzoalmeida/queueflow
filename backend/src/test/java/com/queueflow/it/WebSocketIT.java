package com.queueflow.it;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebSocket/STOMP integration over real PostgreSQL (queueflow_test on :5433).
 * Assumes the same fresh-DB, sequential-class suite as FlowIT (FlowIT runs first
 * alphabetically and leaves queues/users in place); defensive setup reuses or
 * creates what it needs through the public/admin APIs, never raw SQL inserts.
 *
 * Covers: broadcast of TICKET_ISSUED/TICKET_CALLED on /topic/display after commit,
 * subscription authorization on /topic/attendant (denied anonymous, allowed with
 * an admin session cookie on the handshake).
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebSocketIT {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    final ObjectMapper mapper = new ObjectMapper();

    static String adminSession, wsAttSession;
    static Long wsQueueId, wsCounterId;

    ResponseEntity<JsonNode> post(String path, Object body, String session) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        if (session != null) h.add(HttpHeaders.COOKIE, session);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), JsonNode.class);
    }

    String sessionCookie(ResponseEntity<?> response) {
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie, "authentication response must set a session cookie");
        return setCookie.split(";", 2)[0];
    }

    /** Collects every STOMP frame body as a String for later assertions. */
    static class RecordingHandler implements StompFrameHandler {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        @Override public Type getPayloadType(StompHeaders headers) { return byte[].class; }
        @Override public void handleFrame(StompHeaders headers, Object payload) {
            messages.add(new String((byte[]) payload, StandardCharsets.UTF_8));
        }
        String poll() throws InterruptedException { return messages.poll(10, TimeUnit.SECONDS); }
    }

    /** Records ERROR frames / transport failures (e.g. denied SUBSCRIBE). */
    static class FailureWatch extends StompSessionHandlerAdapter {
        final CountDownLatch failed = new CountDownLatch(1);
        volatile String reason;
        @Override public void handleException(StompSession session, StompCommand command,
                                              StompHeaders headers, byte[] payload, Throwable ex) {
            reason = String.valueOf(ex.getMessage());
            failed.countDown();
        }
        @Override public void handleTransportError(StompSession session, Throwable ex) {
            reason = String.valueOf(ex.getMessage());
            failed.countDown();
        }
    }

    WebSocketStompClient newClient() {
        return new WebSocketStompClient(new SockJsClient(
                List.of(new WebSocketTransport(new StandardWebSocketClient()))));
    }

    @Test
    @Order(0)
    void setup_queueAttendantAndCounter() {
        // reutiliza uma fila existente (FlowIT) ou cria uma via bootstrap admin
        var queues = rest.getForEntity("/api/public/queues", JsonNode.class);
        if (queues.getBody() != null && queues.getBody().isArray() && !queues.getBody().isEmpty()) {
            wsQueueId = queues.getBody().get(0).path("id").asLong();
        }
        var adminLogin = post("/api/auth/login", Map.of("email", "admin@test.io", "password", "Secret1!23456"), null);
        if (adminLogin.getStatusCode() == HttpStatus.OK) {
            adminSession = sessionCookie(adminLogin);
        } else {
            // suíte rodando só esta classe: bootstrap do zero
            var reg = post("/api/auth/register",
                    Map.of("name", "Admin", "email", "admin@test.io", "password", "Secret1!23456"), null);
            assertEquals(HttpStatus.OK, reg.getStatusCode());
            adminSession = sessionCookie(reg);
        }
        if (wsQueueId == null) {
            var q = post("/api/queues", Map.of("name", "Fila WS", "prefix", "W"), adminSession);
            assertEquals(HttpStatus.OK, q.getStatusCode());
            wsQueueId = q.getBody().path("id").asLong();
        }
        var mk = post("/api/users",
                Map.of("name", "Atendente WS", "email", "ws-att@test.io", "password", "Secret1!23456"), adminSession);
        if (mk.getStatusCode() != HttpStatus.OK && mk.getStatusCode() != HttpStatus.CONFLICT) {
            fail("criação do atendente WS falhou: " + mk.getStatusCode());
        }
        var login = post("/api/auth/login", Map.of("email", "ws-att@test.io", "password", "Secret1!23456"), null);
        if (login.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            // senha já trocada em execução anterior na mesma base
            login = post("/api/auth/login", Map.of("email", "ws-att@test.io", "password", "NovaSenhaWs1!"), null);
        }
        assertEquals(HttpStatus.OK, login.getStatusCode());
        String session = sessionCookie(login);
        if (login.getBody().path("mustChangePassword").asBoolean()) {
            var changed = post("/api/auth/change-password",
                    Map.of("currentPassword", "Secret1!23456", "newPassword", "NovaSenhaWs1!"), session);
            assertEquals(HttpStatus.OK, changed.getStatusCode());
            session = sessionCookie(changed);
        }
        wsAttSession = session;
        var c = post("/api/counters", Map.of("name", "Guichê WS"), adminSession);
        if (c.getStatusCode() == HttpStatus.OK) {
            wsCounterId = c.getBody().path("id").asLong();
        } else {
            // nome já existe de execução anterior na mesma base: reutiliza
            var list = rest.exchange("/api/counters", HttpMethod.GET,
                    new HttpEntity<>(null, new HttpHeaders() {{ add(HttpHeaders.COOKIE, adminSession); }}), JsonNode.class);
            for (JsonNode node : list.getBody()) if ("Guichê WS".equals(node.path("name").asText())) wsCounterId = node.path("id").asLong();
            assertNotNull(wsCounterId, "guichê WS não encontrado");
        }
    }

    @Test
    @Order(1)
    void displayTopic_broadcastsTicketIssuedToAnonymousClients() throws Exception {
        var client = newClient();
        var session = client.connectAsync("http://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(), new StompHeaders(), new FailureWatch()).get(10, TimeUnit.SECONDS);
        var rec = new RecordingHandler();
        session.subscribe("/topic/display", rec);
        Thread.sleep(300); // garante SUBSCRIBE processado antes do evento

        var issued = post("/api/public/tickets", Map.of("queueId", wsQueueId, "priorityType", "NORMAL"), null);
        assertEquals(HttpStatus.OK, issued.getStatusCode());
        String code = issued.getBody().path("displayCode").asText();

        String msg = rec.poll();
        assertNotNull(msg, "nenhum frame recebido em /topic/display após emitir senha");
        JsonNode json = mapper.readTree(msg);
        assertEquals("TICKET_ISSUED", json.path("type").asText());
        assertEquals(code, json.path("payload").path("displayCode").asText(),
                "payload do evento deve carregar a senha emitida");
        session.disconnect();
    }

    @Test
    @Order(2)
    void attendantTopic_rejectsAnonymousSubscription() throws Exception {
        var client = newClient();
        var watch = new FailureWatch();
        var session = client.connectAsync("http://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(), new StompHeaders(), watch).get(10, TimeUnit.SECONDS);
        session.subscribe("/topic/attendant", new RecordingHandler());
        assertTrue(watch.failed.await(10, TimeUnit.SECONDS),
                "SUBSCRIBE anônimo em /topic/attendant deveria ser negado");
    }

    @Test
    @Order(3)
    void attendantTopic_acceptsAdminSession_andDisplayShowsCall() throws Exception {
        // claim do guichê pelo atendente
        var claim = post("/api/counters/" + wsCounterId + "/claim", null, wsAttSession);
        assertTrue(claim.getStatusCode().is2xxSuccessful() || claim.getStatusCode() == HttpStatus.CONFLICT,
                "claim falhou: " + claim.getStatusCode());

        WebSocketHttpHeaders authHeaders = new WebSocketHttpHeaders();
        authHeaders.add(HttpHeaders.COOKIE, adminSession);
        var client = newClient();
        var adminWatch = new FailureWatch();
        var session = client.connectAsync("http://localhost:" + port + "/ws",
                authHeaders, new StompHeaders(), adminWatch).get(10, TimeUnit.SECONDS);
        var rec = new RecordingHandler();
        session.subscribe("/topic/attendant", rec); // não deve falhar
        var displayRec = new RecordingHandler();
        session.subscribe("/topic/display", displayRec);
        Thread.sleep(300);
        assertEquals(1, adminWatch.failed.getCount(), "SUBSCRIBE autenticado em /topic/attendant falhou");

        var issued = post("/api/public/tickets", Map.of("queueId", wsQueueId, "priorityType", "NORMAL"), null);
        assertEquals(HttpStatus.OK, issued.getStatusCode());

        var called = post("/api/tickets/call-next", Map.of("counterId", wsCounterId), wsAttSession);
        assertEquals(HttpStatus.OK, called.getStatusCode(), "call-next falhou: " + called.getBody());
        // FIFO: a chamada pega a senha mais antiga da fila, que pode ser de um teste anterior
        String code = called.getBody().path("displayCode").asText();

        String msg = null, frame;
        while ((frame = displayRec.poll()) != null) {
            JsonNode json = mapper.readTree(frame);
            if ("TICKET_CALLED".equals(json.path("type").asText())
                    && code.equals(json.path("payload").path("ticket").path("displayCode").asText())) {
                msg = frame;
                break;
            }
        }
        assertNotNull(msg, "TICKET_CALLED da senha " + code + " não chegou em /topic/display");
        session.disconnect();

        // limpeza: libera o guichê para não vazar estado para outras classes
        post("/api/counters/release", null, wsAttSession);
    }
}

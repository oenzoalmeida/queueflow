package com.queueflow.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mandatory first-login password change (users.must_change_password):
 * a user provisioned with a temporary password can only reach /api/auth/me,
 * /api/auth/logout and /api/auth/change-password until the flag clears.
 * Runs against real PostgreSQL (queueflow_test on :5433) and cleans up its own rows.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ForcedChangeIT {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    static final String EMAIL = "forced-admin@test.io";
    static final String OTHER = "forced-other@test.io";
    static final String TEMP = "Temp123!";
    static final String NEW = "NovaSenha456!";
    static String session;

    ResponseEntity<JsonNode> post(String path, Object body, String session) {
        return exchange(HttpMethod.POST, path, body, session);
    }

    ResponseEntity<JsonNode> get(String path, String session) {
        return exchange(HttpMethod.GET, path, null, session);
    }

    ResponseEntity<JsonNode> exchange(HttpMethod method, String path, Object body, String session) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (session != null) h.add(HttpHeaders.COOKIE, session);
        return rest.exchange(path, method, new HttpEntity<>(body, h), JsonNode.class);
    }

    String sessionCookie(ResponseEntity<?> response) {
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie, "authentication response must set a session cookie");
        return setCookie.split(";", 2)[0];
    }

    @Test
    @Order(99)
    void cleanupRows() {
        jdbc.update("DELETE FROM users WHERE email IN (?, ?)", EMAIL, OTHER);
    }

    @Test
    @Order(1)
    void provisionedUserWithFlag_logsInAndSeesFlag() {
        jdbc.update("INSERT INTO users (name, email, password_hash, role, active, must_change_password, created_at) VALUES (?,?,?,?,?,?,now())",
                "Forced Admin", EMAIL, encoder.encode(TEMP), "ADMIN", true, true);

        var login = post("/api/auth/login", Map.of("email", EMAIL, "password", TEMP), null);
        assertEquals(HttpStatus.OK, login.getStatusCode());
        assertTrue(login.getBody().path("mustChangePassword").asBoolean(), "login must report mustChangePassword");
        session = sessionCookie(login);
    }

    @Test
    @Order(2)
    void businessEndpointsBlockedWhileFlagSet_meAndLogoutAllowed() {
        var queues = get("/api/queues", session);
        assertEquals(HttpStatus.FORBIDDEN, queues.getStatusCode());
        assertEquals("PASSWORD_CHANGE_REQUIRED", queues.getBody().path("code").asText());

        var users = get("/api/users", session);
        assertEquals(HttpStatus.FORBIDDEN, users.getStatusCode());

        var tickets = get("/api/tickets", session);
        assertEquals(HttpStatus.FORBIDDEN, tickets.getStatusCode());

        var me = get("/api/auth/me", session);
        assertEquals(HttpStatus.OK, me.getStatusCode());
        assertTrue(me.getBody().path("mustChangePassword").asBoolean());
    }

    @Test
    @Order(3)
    void changePasswordRejectsWrongCurrent() {
        var r = post("/api/auth/change-password", Map.of("currentPassword", "errada", "newPassword", NEW), session);
        assertEquals(HttpStatus.UNAUTHORIZED, r.getStatusCode());
    }

    @Test
    @Order(4)
    void changePasswordRejectsSameAsCurrent_flagStays() {
        var r = post("/api/auth/change-password", Map.of("currentPassword", TEMP, "newPassword", TEMP), session);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, r.getStatusCode());

        var me = get("/api/auth/me", session);
        assertTrue(me.getBody().path("mustChangePassword").asBoolean(), "flag must survive a same-password attempt");
        assertEquals(HttpStatus.FORBIDDEN, get("/api/queues", session).getStatusCode());
    }

    @Test
    @Order(5)
    void changePasswordRejectsWeak_flagStays() {
        var r = post("/api/auth/change-password", Map.of("currentPassword", TEMP, "newPassword", "123"), session);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, r.getStatusCode(), "bean validation rejects a weak new password");
        assertTrue(get("/api/auth/me", session).getBody().path("mustChangePassword").asBoolean());
    }

    @Test
    @Order(6)
    void validChangeClearsFlag_oldPasswordStopsWorking() {
        var r = post("/api/auth/change-password", Map.of("currentPassword", TEMP, "newPassword", NEW), session);
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertFalse(r.getBody().path("mustChangePassword").asBoolean());
        String newSession = sessionCookie(r);

        assertEquals(HttpStatus.OK, get("/api/queues", newSession).getStatusCode(), "business endpoints open after the change");
        assertFalse(get("/api/auth/me", newSession).getBody().path("mustChangePassword").asBoolean());

        var oldLogin = post("/api/auth/login", Map.of("email", EMAIL, "password", TEMP), null);
        assertEquals(HttpStatus.UNAUTHORIZED, oldLogin.getStatusCode(), "temporary password must die after the change");

        var newLogin = post("/api/auth/login", Map.of("email", EMAIL, "password", NEW), null);
        assertEquals(HttpStatus.OK, newLogin.getStatusCode());
        assertFalse(newLogin.getBody().path("mustChangePassword").asBoolean());
    }

    @Test
    @Order(7)
    void selfRegisteredUserHasNoFlag() {
        var reg = post("/api/auth/register", Map.of("name", "Sem Flag", "email", OTHER, "password", "Secret1!"), null);
        assertEquals(HttpStatus.OK, reg.getStatusCode());
        assertFalse(reg.getBody().path("mustChangePassword").asBoolean(), "self-register must not set the flag");
    }
}

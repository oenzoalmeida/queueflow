package com.queueflow.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Filtro de rate limit habilitado de ponta a ponta: 429 com Retry-After (sem retry/espera do cliente). */
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.ratelimit.enabled=true", "app.ratelimit.limit-per-minute=3"})
class RateLimitIT {

    @Autowired TestRestTemplate rest;

    @Test
    void loginIsLimitedWith429AndRetryAfter() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("X-Forwarded-For", "203.0.113.77");
        var body = Map.of("email", "nobody@test.io", "password", "wrong");
        for (int i = 0; i < 3; i++) {
            var r = rest.exchange("/api/auth/login", HttpMethod.POST, new HttpEntity<>(body, h), String.class);
            assertEquals(HttpStatus.UNAUTHORIZED, r.getStatusCode());
        }
        long t0 = System.currentTimeMillis();
        var limited = rest.exchange("/api/auth/login", HttpMethod.POST, new HttpEntity<>(body, h), String.class);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, limited.getStatusCode());
        assertNotNull(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertTrue(System.currentTimeMillis() - t0 < 5_000, "client must not wait for Retry-After");
    }

    @Test
    void spoofedFirstXffEntriesStayInTheSameBucket() {
        // O TestRestTemplate conecta de 127.0.0.1 (proxy confiável): o filtro deve olhar o
        // ÚLTIMO item do XFF. Variar os itens anteriores não pode zerar a cota do atacante.
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        var body = Map.of("email", "nobody@test.io", "password", "wrong");
        for (int i = 1; i <= 3; i++) {
            h.set("X-Forwarded-For", "10.1.2." + i + ", 203.0.113.88");
            var r = rest.exchange("/api/auth/login", HttpMethod.POST, new HttpEntity<>(body, h), String.class);
            assertEquals(HttpStatus.UNAUTHORIZED, r.getStatusCode());
        }
        h.set("X-Forwarded-For", "10.9.9.9, 203.0.113.88");
        var limited = rest.exchange("/api/auth/login", HttpMethod.POST, new HttpEntity<>(body, h), String.class);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, limited.getStatusCode());
    }
}

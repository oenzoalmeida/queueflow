package com.queueflow.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/** Interface servida pela mesma origem da API (login funciona no Safari, sem cookie de terceiros). */
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SameOriginWebIT {

    @Autowired TestRestTemplate rest;
    @LocalServerPort int port;

    @Test
    void rootAndClientRoutesServeIndexWithoutLogin() {
        for (String path : new String[] {"/", "/login", "/totem", "/admin/queues"}) {
            var r = rest.getForEntity(path, String.class);
            assertEquals(HttpStatus.OK, r.getStatusCode(), path);
            assertTrue(r.getBody().contains("queueflow-test-index"), path);
        }
    }

    @Test
    void apiAndWsNeverFallBackToIndex() {
        var api = rest.getForEntity("/api/auth/me", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, api.getStatusCode());
        var missingAsset = rest.getForEntity("/assets/nao-existe.js", String.class);
        assertEquals(HttpStatus.NOT_FOUND, missingAsset.getStatusCode());
    }

    @Test
    void cookieMutationFromForeignOriginIsRejectedButOwnOriginPasses() {
        HttpHeaders foreign = new HttpHeaders();
        foreign.add(HttpHeaders.COOKIE, "qf_session=x");
        foreign.add(HttpHeaders.ORIGIN, "https://evil.example");
        var denied = rest.exchange("/api/auth/logout", HttpMethod.POST, new HttpEntity<>(foreign), String.class);
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());

        HttpHeaders own = new HttpHeaders();
        own.add(HttpHeaders.COOKIE, "qf_session=x");
        own.add(HttpHeaders.ORIGIN, "http://localhost:" + port);
        var ok = rest.exchange("/api/auth/logout", HttpMethod.POST, new HttpEntity<>(own), String.class);
        assertEquals(HttpStatus.NO_CONTENT, ok.getStatusCode());
    }
}

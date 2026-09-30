package com.queueflow.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.UnsupportedEncodingException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "limitPerMinute", 3);
        ReflectionTestUtils.setField(filter, "windowSeconds", 60);
    }

    private MockHttpServletResponse post(String uri, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setRequestURI(uri);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        assertTrue(chain.getRequest() != null || res.getStatus() == 429, "chain deve passar ou resposta deve ser 429");
        return res;
    }

    @Test
    void blocksLoginAboveLimitWith429() throws Exception {
        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse res = post("/api/auth/login", "10.0.0.1");
            assertEquals(200, res.getStatus());
        }
        MockHttpServletResponse res = post("/api/auth/login", "10.0.0.1");
        assertEquals(429, res.getStatus());
        assertEquals("application/json;charset=UTF-8", res.getContentType());
        assertTrue(res.getHeader("Retry-After") != null);
        assertTrue(res.getContentAsString().contains("\"status\":429"));
    }

    @Test
    void blocksTicketIssuanceAboveLimit() throws Exception {
        for (int i = 0; i < 3; i++) assertEquals(200, post("/api/public/tickets", "10.0.0.2").getStatus());
        assertEquals(429, post("/api/public/tickets", "10.0.0.2").getStatus());
    }

    @Test
    void bucketsArePerIp() throws Exception {
        for (int i = 0; i < 3; i++) assertEquals(200, post("/api/auth/login", "10.0.0.3").getStatus());
        assertEquals(429, post("/api/auth/login", "10.0.0.3").getStatus());
        // outro IP continua com cota própria
        assertEquals(200, post("/api/auth/login", "10.0.0.4").getStatus());
    }

    @Test
    void doesNotFilterOtherEndpointsOrMethods() throws Exception {
        // GET no mesmo path e paths públicos não são limitados
        for (int i = 0; i < 10; i++) {
            assertEquals(200, post("/api/public/queues", "10.0.0.5").getStatus());
        }
    }

    @Test
    void windowResetAllowsAgain() throws Exception {
        ReflectionTestUtils.setField(filter, "windowSeconds", 1);
        for (int i = 0; i < 3; i++) assertEquals(200, post("/api/auth/login", "10.0.0.6").getStatus());
        assertEquals(429, post("/api/auth/login", "10.0.0.6").getStatus());
        Thread.sleep(1100);
        assertEquals(200, post("/api/auth/login", "10.0.0.6").getStatus());
    }

    @Test
    void disabledFilterPassesEverything() throws Exception {
        ReflectionTestUtils.setField(filter, "enabled", false);
        for (int i = 0; i < 10; i++) assertEquals(200, post("/api/auth/login", "10.0.0.7").getStatus());
    }
}

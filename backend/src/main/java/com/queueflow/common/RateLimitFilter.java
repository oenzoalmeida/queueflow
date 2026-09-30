package com.queueflow.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limit simples in-memory (janela fixa por IP) para os endpoints públicos sensíveis:
 * login (força bruta de credenciais) e emissão anônima de senhas (abuso do totem).
 *
 * Não interfere em WebSocket/SockJS/CORS com credentials: atua somente em POST nos
 * caminhos mapeados abaixo; preflights (OPTIONS) e todo o resto passam intocados.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    /** method + URI -> rótulo do bucket. */
    private static final Map<String, String> PROTECTED = Map.of(
            "POST /api/auth/login", "login",
            "POST /api/public/tickets", "tickets");

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Value("${app.ratelimit.enabled:true}")
    private boolean enabled;

    @Value("${app.ratelimit.limit-per-minute:10}")
    private int limitPerMinute;

    @Value("${app.ratelimit.window-seconds:60}")
    private long windowSeconds;

    private record Window(AtomicLong start, AtomicInteger count) {}

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) return true;
        return PROTECTED.get(request.getMethod() + " " + request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String bucket = PROTECTED.get(request.getMethod() + " " + request.getRequestURI());
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;
        Window w = windows.computeIfAbsent(bucket + ":" + clientIp(request),
                k -> new Window(new AtomicLong(now), new AtomicInteger(0)));

        long start = w.start().get();
        if (now - start >= windowMs) {
            // Janela expirada: apenas um concorrente reinicia (CAS) para não duplicar a cota.
            if (w.start().compareAndSet(start, now)) w.count().set(0);
            start = w.start().get();
        }
        if (windows.size() > 10_000) sweep(now, windowMs);
        if (w.count().incrementAndGet() > limitPerMinute) {
            long retryAfter = Math.max(1, (windowMs - (now - start)) / 1000);
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.getWriter().write("{\"status\":429,\"message\":\"Muitas requisições. Tente novamente em instantes.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private void sweep(long now, long windowMs) {
        windows.entrySet().removeIf(e -> now - e.getValue().start().get() >= windowMs);
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return request.getRemoteAddr();
    }
}

package com.queueflow.config;

import com.queueflow.auth.AuthController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Protecao contra CSRF para autenticacao via cookie.
 *
 * Em producao o cookie de sessao vai com SameSite=None (frontend e API em
 * origens diferentes), entao o navegador anexa o cookie mesmo em requisicoes
 * cross-site forjadas. Como o app usa exclusivamente cookie httpOnly, este
 * filtro exige que mutacoes (POST/PUT/PATCH/DELETE) que carregam o cookie de
 * sessao tragam um header Origin (ou Referer) pertencente a mesma lista de
 * origens liberadas no CORS (app.cors.allowed-origins).
 *
 * Requisicoes sem cookie (ex.: login/register, clientes de API fora do
 * navegador) e metodos seguros (GET/HEAD/OPTIONS) nao sao afetados.
 */
@Component
public class OriginRefererCsrfFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final Set<String> allowedOrigins;

    public OriginRefererCsrfFilter(@Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(OriginRefererCsrfFilter::normalize)
                .collect(Collectors.toSet());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (path != null && path.startsWith("/ws/")) {
            return true; // handshakes/transports SockJS; CORS ja restringe a origem
        }
        return !hasSessionCookie(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String rawOrigin = request.getHeader("Origin");
        if (rawOrigin != null && !rawOrigin.isBlank()) {
            // Navegadores sempre enviam Origin em mutações cross-site: valor
            // presente precisa estar na allowlist ("null" nunca é permitido).
            String origin = normalizeHeaderOrigin(rawOrigin);
            if (origin != null && allowedOrigins.contains(origin)) {
                filterChain.doFilter(request, response);
            } else {
                reject(response);
            }
            return;
        }
        String refererOrigin = originOf(request.getHeader("Referer"));
        if (refererOrigin != null) {
            if (allowedOrigins.contains(refererOrigin)) {
                filterChain.doFilter(request, response);
            } else {
                reject(response);
            }
            return;
        }
        // Sem Origin nem Referer: cliente fora do navegador (curl, testes, e2e).
        filterChain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=" + StandardCharsets.UTF_8);
        response.getWriter().write("{\"status\":403,\"message\":\"Origem da requisição não permitida.\"}");
    }

    private boolean hasSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie c : cookies) {
            if (AuthController.SESSION_COOKIE.equals(c.getName())) {
                return true;
            }
        }
        return false;
    }

    private static String originOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(uri.getScheme().toLowerCase()).append("://").append(uri.getHost().toLowerCase());
            if (uri.getPort() >= 0) {
                sb.append(':').append(uri.getPort());
            }
            return sb.toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String normalizeHeaderOrigin(String origin) {
        if (origin == null || origin.isBlank() || "null".equalsIgnoreCase(origin.trim())) {
            return null;
        }
        return normalize(origin.trim());
    }

    private static String normalize(String origin) {
        String o = origin.trim();
        while (o.endsWith("/")) {
            o = o.substring(0, o.length() - 1);
        }
        return o.toLowerCase();
    }
}

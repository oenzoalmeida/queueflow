package com.queueflow.config;

import com.queueflow.auth.AuthController;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = cookieToken(req);
            if (token == null) token = bearerToken(req);

            if (token != null) {
                var claims = jwtService.parse(token);
                if (claims != null) {
                    var userOpt = userRepository.findByEmailIgnoreCase(claims.getSubject());
                    if (userOpt.isPresent()) {
                        User user = userOpt.get();
                        if (user.isActive()) {
                            if (user.isMustChangePassword() && !isPasswordChangePath(req)) {
                                writePasswordChangeRequired(res);
                                return;
                            }
                            authenticate(user);
                        }
                    }
                }
            }
        }
        chain.doFilter(req, res);
    }

    private String cookieToken(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        return Arrays.stream(cookies)
                .filter(c -> AuthController.SESSION_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String bearerToken(HttpServletRequest req) {
        String header = req.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }

    public static void authenticate(User user) {
        var auth = new UsernamePasswordAuthenticationToken(
                new AuthUser(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isMustChangePassword()),
                null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // a user provisioned with a temporary password may only reach the change flow, /me and logout until the flag clears
    private static boolean isPasswordChangePath(HttpServletRequest req) {
        String path = req.getRequestURI();
        return path.equals("/api/auth/change-password")
                || path.equals("/api/auth/me")
                || path.equals("/api/auth/logout");
    }

    private static void writePasswordChangeRequired(HttpServletResponse res) throws IOException {
        if (res.isCommitted()) return;
        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"status\":403,\"code\":\"PASSWORD_CHANGE_REQUIRED\",\"message\":\"Troca de senha obrigatória no primeiro acesso.\"}");
    }

    public record AuthUser(Long id, String name, String email, User.Role role, boolean mustChangePassword) {
        public boolean isAdmin() { return role == User.Role.ADMIN; }
    }
}

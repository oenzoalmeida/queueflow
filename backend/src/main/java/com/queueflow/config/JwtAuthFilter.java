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
                    userRepository.findByEmailIgnoreCase(claims.getSubject()).ifPresent(user -> {
                        if (user.isActive()) authenticate(user);
                    });
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
                new AuthUser(user.getId(), user.getName(), user.getEmail(), user.getRole()),
                null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    public record AuthUser(Long id, String name, String email, User.Role role) {
        public boolean isAdmin() { return role == User.Role.ADMIN; }
    }
}

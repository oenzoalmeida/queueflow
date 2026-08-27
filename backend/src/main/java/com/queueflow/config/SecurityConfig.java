package com.queueflow.config;

import com.queueflow.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        var jsonEntry = new org.springframework.security.web.AuthenticationEntryPoint() {
            public void commence(jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse res,
                                 org.springframework.security.core.AuthenticationException e) throws java.io.IOException {
                writeJson(res, HttpStatus.UNAUTHORIZED, "Não autenticado.");
            }
        };
        var jsonDenied = new org.springframework.security.web.access.AccessDeniedHandler() {
            public void handle(jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse res,
                               org.springframework.security.access.AccessDeniedException e) throws java.io.IOException {
                writeJson(res, HttpStatus.FORBIDDEN, "Acesso negado.");
            }
        };
        http.csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(jsonEntry).accessDeniedHandler(jsonDenied))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/api/public/**", "/ws/**", "/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/counters", "/api/counters/{id}").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/counters/*/claim", "/api/counters/release").authenticated()
                .requestMatchers("/api/queues/**", "/api/users/**", "/api/history/**",
                                 "/api/dashboard/**", "/api/settings/**", "/api/counters/**")
                    .hasRole("ADMIN")
                .requestMatchers("/api/tickets/**").hasAnyRole("ADMIN", "ATTENDANT")
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private CorsConfigurationSource corsSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        cfg.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true); // SockJS's XHR-based transports send withCredentials for /ws/**
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }

    private static void writeJson(jakarta.servlet.http.HttpServletResponse res, HttpStatus status, String message)
            throws java.io.IOException {
        res.setStatus(status.value());
        res.setContentType("application/json;charset=" + StandardCharsets.UTF_8);
        res.getWriter().write("{\"status\":%d,\"message\":\"%s\"}".formatted(status.value(), message));
    }
}

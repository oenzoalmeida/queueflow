package com.queueflow.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.queueflow.user.User;
import com.queueflow.common.ApiException;
import com.queueflow.config.JwtAuthFilter.AuthUser;
import com.queueflow.config.JwtService;
import com.queueflow.establishment.EstablishmentRepository;
import com.queueflow.user.UserRepository;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository users;
    private final EstablishmentRepository establishments;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public record RegisterReq(
            @NotBlank(message = "Informe o nome") String name,
            @NotBlank @Email(message = "Email inválido") String email,
            @NotBlank @Size(min = 6, max = 72, message = "A senha deve ter entre 6 e 72 caracteres") String password) {}
    public record LoginReq(@NotBlank @Email String email, @NotBlank String password) {}
    public record LoginResp(String token, Long id, String name, String email, String role) {
        public static LoginResp of(User u, String token) { return new LoginResp(token, u.getId(), u.getName(), u.getEmail(), u.getRole().name()); }
    }

    @PostMapping("/register")
    public LoginResp register(@RequestBody RegisterReq req) {
        if (users.existsByEmailIgnoreCase(req.email().trim())) throw new ApiException(HttpStatus.CONFLICT, "Email já cadastrado.");
        boolean firstUser = users.count() == 0;
        User user = User.builder()
                .name(req.name().trim())
                .email(req.email().trim())
                .passwordHash(encoder.encode(req.password()))
                .role(firstUser ? User.Role.ADMIN : User.Role.ATTENDANT)
                .active(true)
                .createdAt(Instant.now())
                .build();
        if (firstUser && establishments.count() == 0) {
            establishments.save(com.queueflow.establishment.Establishment.builder()
                    .name("Estabelecimento Principal").createdAt(Instant.now()).build());
        }
        return LoginResp.of(users.save(user), jwt.generate(user.getEmail(), user.getRole().name()));
    }

    @PostMapping("/login")
    public LoginResp login(@RequestBody LoginReq req) {
        User user = users.findByEmailIgnoreCase(req.email().trim())
                .orElseThrow(() -> new BadCredentialsException("Credenciais inválidas."));
        if (!user.isActive()) throw new ApiException(HttpStatus.FORBIDDEN, "Usuário desativado. Contate um administrador.");
        if (!encoder.matches(req.password(), user.getPasswordHash())) throw new BadCredentialsException("Credenciais inválidas.");
        return LoginResp.of(user, jwt.generate(user.getEmail(), user.getRole().name()));
    }

    @GetMapping("/me")
    public Map<String, Object> me(java.security.Principal principal) {
        AuthUser au = (AuthUser) ((org.springframework.security.core.Authentication) principal).getPrincipal();
        return Map.of("id", au.id(), "name", au.name(), "email", au.email(), "role", au.role().name());
    }
}

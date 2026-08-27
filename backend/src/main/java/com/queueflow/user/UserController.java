package com.queueflow.user;

import com.queueflow.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository users;
    private final org.springframework.security.crypto.password.PasswordEncoder encoder;

    public record CreateReq(@NotBlank @Size(max = 120) String name,
                            @NotBlank @Email String email,
                            @NotBlank @Size(min = 6, max = 72) String password) {}
    public record UpdateReq(@Size(max = 120) String name,
                            @Email String email,
                            @Size(min = 6, max = 72) String password,
                            Boolean active) {}
    public record UserDTO(Long id, String name, String email, String role, boolean active, Instant createdAt) {
        static UserDTO of(User u) { return new UserDTO(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.isActive(), u.getCreatedAt()); }
    }

    @GetMapping
    public List<UserDTO> list() { return users.findAllByOrderByNameAsc().stream().map(UserDTO::of).toList(); }

    @PostMapping
    public UserDTO create(@Valid @RequestBody CreateReq req) {
        if (users.existsByEmailIgnoreCase(req.email().trim()))
            throw new ApiException(HttpStatus.CONFLICT, "Email já cadastrado.");
        return UserDTO.of(users.save(User.builder()
                .name(req.name().trim()).email(req.email().trim())
                .passwordHash(encoder.encode(req.password()))
                .role(User.Role.ATTENDANT).active(true).createdAt(Instant.now()).build()));
    }

    @PutMapping("/{id}")
    public UserDTO update(@PathVariable Long id, @Valid @RequestBody UpdateReq req) {
        User u = users.findById(id).orElseThrow(() -> new ApiException.NotFound("Usuário não encontrado."));
        if (req.email() != null) {
            var other = users.findByEmailIgnoreCase(req.email().trim());
            if (other.isPresent() && !other.get().getId().equals(id))
                throw new ApiException(HttpStatus.CONFLICT, "Email já cadastrado.");
            u.setEmail(req.email().trim());
        }
        if (req.name() != null && !req.name().isBlank()) u.setName(req.name().trim());
        if (req.password() != null && !req.password().isBlank()) {
            if (req.password().length() < 6) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Senha deve ter no mínimo 6 caracteres.");
            u.setPasswordHash(encoder.encode(req.password()));
        }
        if (req.active() != null) {
            if (!req.active() && u.getRole() == User.Role.ADMIN)
                throw new ApiException.Rule("Não é possível desativar um administrador.");
            u.setActive(req.active());
        }
        return UserDTO.of(users.save(u));
    }
}

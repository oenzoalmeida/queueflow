package com.queueflow.counter;

import com.queueflow.common.ApiException;
import com.queueflow.config.JwtAuthFilter.AuthUser;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/counters")
@RequiredArgsConstructor
public class CounterController {

    private final CounterRepository counters;
    private final UserRepository users;

    public record CounterReq(@NotBlank(message = "Informe o nome do guichê") @Size(max = 80) String name,
                             boolean active) {}

    public record CounterDTO(Long id, String name, boolean active, String currentAttendantName) {}

    private static CounterDTO dto(Counter c) {
        return new CounterDTO(c.getId(), c.getName(), c.isActive(),
                c.getCurrentAttendant() != null ? c.getCurrentAttendant().getName() : null);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CounterDTO> list() {
        return counters.findAllByOrderByNameAsc().stream().map(CounterController::dto).toList();
    }

    @PostMapping
    public CounterDTO create(@Valid @RequestBody CounterReq req) {
        if (counters.findByNameIgnoreCase(req.name().trim()).isPresent())
            throw new ApiException(HttpStatus.CONFLICT, "Já existe um guichê com esse nome.");
        return dto(counters.save(Counter.builder().name(req.name().trim())
                .active(true).createdAt(Instant.now()).build()));
    }

    @PutMapping("/{id}")
    public CounterDTO update(@PathVariable Long id, @Valid @RequestBody CounterReq req) {
        Counter c = counters.findById(id).orElseThrow(() -> new ApiException.NotFound("Guichê não encontrado."));
        var same = counters.findByNameIgnoreCase(req.name().trim());
        if (same.isPresent() && !same.get().getId().equals(id))
            throw new ApiException(HttpStatus.CONFLICT, "Já existe um guichê com esse nome.");
        c.setName(req.name().trim());
        c.setActive(req.active());
        return dto(counters.save(c));
    }

    /** Attendant selects a counter. Refuses if another attendant occupies it. */
    @PostMapping("/{id}/claim")
    @Transactional
    public Map<String, Object> claim(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        Counter target = counters.findById(id).orElseThrow(() -> new ApiException.NotFound("Guichê não encontrado."));
        User me = users.findById(user.id()).orElseThrow();
        if (!target.isActive()) throw new ApiException.Rule("Guichê inativo.");
        if (target.getCurrentAttendant() != null && !target.getCurrentAttendant().getId().equals(me.getId()))
            throw new ApiException.Rule("Guichê ocupado por outro atendente.");
        // release any counter previously claimed by me
        for (Counter mine : counters.findAll()) {
            if (mine.getCurrentAttendant() != null && mine.getCurrentAttendant().getId().equals(me.getId())
                    && !mine.getId().equals(target.getId())) {
                mine.setCurrentAttendant(null);
            }
        }
        target.setCurrentAttendant(me);
        counters.save(target);
        return Map.of("ok", true);
    }

    @PostMapping("/release")
    @Transactional
    public Map<String, Object> release(@AuthenticationPrincipal AuthUser user) {
        User me = users.findById(user.id()).orElseThrow();
        for (Counter c : counters.findAll()) {
            if (c.getCurrentAttendant() != null && c.getCurrentAttendant().getId().equals(me.getId()))
                c.setCurrentAttendant(null);
        }
        return Map.of("ok", true);
    }
}

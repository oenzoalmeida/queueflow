package com.queueflow.queue;

import com.queueflow.common.ApiException;
import com.queueflow.ticket.TicketRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/queues")
@RequiredArgsConstructor
public class QueueController {

    private final QueueRepository queues;
    private final TicketRepository tickets;

    public record QueueReq(
            @NotBlank(message = "Informe o nome da fila") @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{1,3}$", message = "Prefixo deve ter 1 a 3 letras") String prefix,
            boolean active) {}

    public record QueueDTO(Long id, String name, String prefix, boolean active, Instant createdAt) {
        static QueueDTO of(Queue q) { return new QueueDTO(q.getId(), q.getName(), q.getPrefix(), q.isActive(), q.getCreatedAt()); }
    }

    @GetMapping
    public List<QueueDTO> list() {
        return queues.findAllByOrderByNameAsc().stream().map(QueueDTO::of).toList();
    }

    @PostMapping
    public QueueDTO create(@Valid @RequestBody QueueReq req) {
        if (queues.existsByNameIgnoreCase(req.name().trim()))
            throw new ApiException(HttpStatus.CONFLICT, "Já existe uma fila com esse nome.");
        if (queues.existsByPrefixIgnoreCase(req.prefix().trim()))
            throw new ApiException(HttpStatus.CONFLICT, "Já existe uma fila com esse prefixo.");
        return QueueDTO.of(queues.save(Queue.builder()
                .name(req.name().trim()).prefix(req.prefix().trim().toUpperCase())
                .active(true).createdAt(Instant.now()).build()));
    }

    @PutMapping("/{id}")
    public QueueDTO update(@PathVariable Long id, @Valid @RequestBody QueueReq req) {
        Queue q = queues.findById(id).orElseThrow(() -> new ApiException.NotFound("Fila não encontrada."));
        if (queues.existsByNameIgnoreCase(req.name().trim()) && !q.getName().equalsIgnoreCase(req.name().trim()))
            throw new ApiException(HttpStatus.CONFLICT, "Já existe uma fila com esse nome.");
        if (queues.existsByPrefixIgnoreCase(req.prefix().trim()) && !q.getPrefix().equalsIgnoreCase(req.prefix().trim()))
            throw new ApiException(HttpStatus.CONFLICT, "Já existe uma fila com esse prefixo.");
        q.setName(req.name().trim());
        q.setPrefix(req.prefix().trim().toUpperCase());
        q.setActive(req.active());
        return QueueDTO.of(queues.save(q));
    }
}

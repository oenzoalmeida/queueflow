package com.queueflow.ticket;

import com.queueflow.common.ApiException;
import com.queueflow.counter.Counter;
import com.queueflow.counter.CounterRepository;
import com.queueflow.event.TicketEvent;
import com.queueflow.event.TicketEventRepository;
import com.queueflow.queue.Queue;
import com.queueflow.queue.QueueRepository;
import com.queueflow.settings.PrioritySettingsRepository;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import com.queueflow.ws.WsPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TicketService {
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final List<Ticket.Status> ACTIVE = List.of(Ticket.Status.CALLED, Ticket.Status.IN_SERVICE);

    private final TicketRepository tickets;
    private final DailySequenceRepository sequences;
    private final PriorityDailySequenceRepository prioritySequences;
    private final QueueRepository queues;
    private final CounterRepository counters;
    private final UserRepository users;
    private final TicketEventRepository events;
    private final PrioritySettingsRepository settings;
    private final WsPublisher ws;

    public record AuthContext(Long userId, Long counterId) {}

    // ---------- issue (totem) ----------

    public record IssuedDTO(String displayCode, long peopleAhead, Integer estimatedWaitMinutes,
                            String queueName, Instant createdAt) {}

    @Transactional
    public Ticket issue(Long queueId, Ticket.PriorityType type) {
        Queue queue = queues.findById(queueId).orElseThrow(() -> new ApiException.NotFound("Fila não encontrada."));
        if (!queue.isActive()) throw new ApiException.Rule("Fila indisponível.");

        LocalDate day = LocalDate.now(ZONE);
        int number;
        String code;
        if (type == Ticket.PriorityType.PRIORITY) {
            // priority tickets share one establishment-wide sequence, independent of each queue's normal numbering
            prioritySequences.ensureRow(day);
            PriorityDailySequence seq = prioritySequences.lockRow(day); // serializes concurrent issuances
            seq.setLastNumber(seq.getLastNumber() + 1);
            number = seq.getLastNumber();
            code = "P%03d".formatted(number);
        } else {
            sequences.ensureRow(queue.getId(), day);
            DailySequence seq = sequences.lockRow(queue.getId(), day); // serializes concurrent issuances
            seq.setLastNumber(seq.getLastNumber() + 1);
            number = seq.getLastNumber();
            code = queue.getPrefix() + "%03d".formatted(number);
        }
        Ticket t = tickets.save(Ticket.builder()
                .queue(queue).number(number).displayCode(code).priorityType(type)
                .status(Ticket.Status.WAITING).createdAt(Instant.now())
                .build());
        saveEvent(t, TicketEvent.Type.CREATED, null, null);
        ws.display("TICKET_ISSUED", toDTO(t));
        return t;
    }

    /** Public info shown right after issuance on the totem. */
    @Transactional(readOnly = true)
    public IssuedDTO issuedInfo(Ticket t) {
        Integer est = estimateFor(t.getId());
        return new IssuedDTO(t.getDisplayCode(), tickets.waitingAhead(t.getQueue().getId(), t.getId()),
                est, t.getQueue().getName(), t.getCreatedAt());
    }

    private long peopleAhead(long ticketId, Long queueId) {
        return tickets.waitingAhead(queueId, ticketId) + tickets.countByStatus(Ticket.Status.IN_SERVICE);
    }

    /**
     * avg recent service time x people ahead of the ticket / active counters.
     * Returns null when there is not enough data ("Estimativa ainda indisponível").
     */
    @Transactional(readOnly = true)
    public Integer estimateFor(long ticketId) {
        var range = todayRange();
        List<Ticket> finished = tickets.finishedBetween(range.from(), range.to(), PageRequest.ofSize(20));
        double avgMs = finished.stream()
                .filter(t -> t.getFinishedAt() != null && t.getServiceStartedAt() != null)
                .mapToLong(t -> t.getFinishedAt().toEpochMilli() - t.getServiceStartedAt().toEpochMilli())
                .average().orElse(Double.NaN);
        if (Double.isNaN(avgMs) || avgMs <= 0) return null;
        Ticket t = tickets.findById(ticketId).orElseThrow(() -> new ApiException.NotFound("Senha não encontrada."));
        long countersActive = Math.max(1, counters.countByActiveTrue());
        double minutes = (avgMs * peopleAhead(ticketId, t.getQueue().getId()) / countersActive) / 60000.0;
        return minutes < 1 ? 1 : (int) Math.round(minutes);
    }

    // ---------- call next (CRITICAL: pessimistic lock + tx) ----------

    @Transactional
    public TicketDTO callNext(AuthContext ctx) {
        User attendant = users.findById(ctx.userId()).orElseThrow();
        Counter counter = counterById(ctx.counterId());
        requireOwnership(counter, attendant);
        if (!counter.isActive()) throw new ApiException.Rule("Guichê inativo.");
        if (tickets.existsByCounterIdAndStatusIn(counter.getId(), ACTIVE))
            throw new ApiException.Rule("Finalize o atendimento atual antes de chamar a próxima senha.");

        ArrayDeque<Ticket> normals = new ArrayDeque<>();
        ArrayDeque<Ticket> priorities = new ArrayDeque<>();
        for (Ticket t : tickets.lockWaiting()) { // SELECT ... FOR UPDATE, serializes concurrent callers
            (t.getPriorityType() == Ticket.PriorityType.PRIORITY ? priorities : normals).addLast(t);
        }
        Ticket chosen = CallPolicy.pick(normals, priorities, normalsSincePriority(),
                        settings.get().getNormalsBeforePriority())
                .orElseThrow(() -> new ApiException.Rule("Nenhuma senha aguardando."));

        chosen.setStatus(Ticket.Status.CALLED);
        chosen.setCalledAt(Instant.now());
        chosen.setCounter(counter);
        chosen.setAttendant(attendant);
        chosen = tickets.save(chosen);
        saveEvent(chosen, TicketEvent.Type.CALLED, attendant, counter);
        ws.display("TICKET_CALLED", Map.of("ticket", toDTO(chosen)));
        return toDTO(chosen);
    }

    /** consecutive NORMAL calls today after the most recent PRIORITY call */
    private int normalsSincePriority() {
        int count = 0;
        var recent = tickets.findByStatusInOrderByCalledAtDesc(
                List.of(Ticket.Status.CALLED, Ticket.Status.IN_SERVICE, Ticket.Status.FINISHED, Ticket.Status.ABSENT),
                PageRequest.ofSize(50));
        for (Ticket t : recent) {
            if (t.getPriorityType() == Ticket.PriorityType.PRIORITY) break;
            if (isToday(t)) ++count;
        }
        return count;
    }

    // ---------- transitions ----------

    @Transactional
    public TicketDTO recall(AuthContext ctx) {
        Ticket t = checkedTransition(ctx, Ticket.Status.CALLED, "Rechamar");
        saveEvent(t, TicketEvent.Type.RECALLED, attendantOf(ctx), counterById(ctx.counterId()));
        ws.display("TICKET_RECALLED", Map.of("ticket", toDTO(t)));
        return toDTO(t);
    }

    @Transactional
    public TicketDTO start(AuthContext ctx) {
        Ticket t = checkedTransition(ctx, Ticket.Status.CALLED, "Iniciar");
        t.setStatus(Ticket.Status.IN_SERVICE);
        t.setServiceStartedAt(Instant.now());
        saveEvent(t, TicketEvent.Type.SERVICE_STARTED, attendantOf(ctx), counterById(ctx.counterId()));
        ws.display("TICKET_STARTED", Map.of("ticket", toDTO(t)));
        return toDTO(t);
    }

    @Transactional
    public TicketDTO finish(AuthContext ctx) {
        Ticket t = checkedTransition(ctx, Ticket.Status.IN_SERVICE, "Finalizar");
        t.setStatus(Ticket.Status.FINISHED);
        t.setFinishedAt(Instant.now());
        saveEvent(t, TicketEvent.Type.FINISHED, attendantOf(ctx), counterById(ctx.counterId()));
        ws.display("TICKET_FINISHED", Map.of("ticket", toDTO(t)));
        return toDTO(t);
    }

    @Transactional
    public TicketDTO markAbsent(AuthContext ctx) {
        Ticket t = checkedTransition(ctx, Ticket.Status.CALLED, "Marcar ausente");
        t.setStatus(Ticket.Status.ABSENT);
        saveEvent(t, TicketEvent.Type.ABSENT, attendantOf(ctx), counterById(ctx.counterId()));
        ws.display("TICKET_ABSENT", Map.of("ticket", toDTO(t)));
        return toDTO(t);
    }

    @Transactional
    public TicketDTO cancel(long id) {
        Ticket t = byId(id);
        if (t.getStatus() == Ticket.Status.FINISHED || t.getStatus() == Ticket.Status.CANCELLED)
            throw new ApiException.Rule("Não é possível cancelar uma senha %s.".formatted(t.getStatus()));
        t.setStatus(Ticket.Status.CANCELLED);
        saveEvent(t, TicketEvent.Type.CANCELLED, null, null);
        ws.display("TICKET_CANCELLED", Map.of("ticket", toDTO(t)));
        return toDTO(t);
    }

    // ---------- reads ----------

    @Transactional(readOnly = true)
    public Ticket currentFor(Long counterId) {
        return tickets.findFirstByCounterIdAndStatusInOrderByCalledAtDesc(counterId, ACTIVE).orElse(null);
    }

    /** Same lookup as {@link #currentFor}, pre-converted to a DTO within the transaction (avoids lazy-init after the session closes). */
    @Transactional(readOnly = true)
    public TicketDTO currentDtoFor(Long counterId) {
        Ticket t = currentFor(counterId);
        return t == null ? null : toDTO(t);
    }

    @Transactional(readOnly = true)
    public long waitingCount() { return tickets.countByStatus(Ticket.Status.WAITING); }

    @Transactional(readOnly = true)
    public record DisplayState(String establishmentName, TicketDTO highlight, List<TicketDTO> lastCalls, long waiting) {}

    // ---------- helpers ----------

    /** Validates current status and that the counter is owned/claimed by the requesting user. */
    private Ticket checkedTransition(AuthContext ctx, Ticket.Status expected, String action) {
        User attendant = attendantOf(ctx);
        Counter counter = counterById(ctx.counterId());
        requireOwnership(counter, attendant);
        Ticket t = tickets.findFirstByCounterIdAndStatusInOrderByCalledAtDesc(counter.getId(), ACTIVE).orElse(null);
        if (t == null || t.getStatus() != expected)
            throw new ApiException.Rule("%s: estado inválido%s.".formatted(action,
                    t == null ? "" : " (atual: %s)".formatted(t.getStatus())));
        // returns fully managed entity
        return tickets.findById(t.getId()).orElseThrow();
    }

    private void requireOwnership(Counter counter, User attendant) {
        if (counter.getCurrentAttendant() == null || !attendant.getId().equals(counter.getCurrentAttendant().getId()))
            throw new ApiException.Rule("Selecione este guichê antes de operar.");
    }

    private User attendantOf(AuthContext ctx) { return users.findById(ctx.userId()).orElseThrow(); }
    private Counter counterById(Long id) {
        return counters.findById(id).orElseThrow(() -> new ApiException.NotFound("Guichê não encontrado."));
    }
    private Ticket byId(long id) { return tickets.findById(id).orElseThrow(() -> new ApiException.NotFound("Senha não encontrada.")); }

    private void saveEvent(Ticket t, TicketEvent.Type type, User att, Counter ctr) {
        events.save(TicketEvent.builder().ticket(t).type(type).attendant(att).counter(ctr)
                .createdAt(Instant.now()).build());
    }

    public TicketDTO toDTO(Ticket t) {
        return new TicketDTO(t.getId(), t.getDisplayCode(), t.getNumber(),
                t.getPriorityType().name(), t.getStatus().name(),
                t.getQueue().getId(), t.getQueue().getName(),
                t.getCounter() != null ? t.getCounter().getName() : null,
                t.getAttendant() != null ? t.getAttendant().getName() : null,
                t.getCreatedAt(), t.getCalledAt(), t.getServiceStartedAt(), t.getFinishedAt());
    }

    private boolean isToday(Ticket t) {
        return t.getCalledAt() != null && t.getCalledAt().atZone(ZONE).toLocalDate().equals(LocalDate.now(ZONE));
    }

    private record Range(Instant from, Instant to) {}
    private Range todayRange() {
        LocalDate d = LocalDate.now(ZONE);
        return new Range(d.atStartOfDay(ZONE).toInstant(), d.plusDays(1).atStartOfDay(ZONE).toInstant());
    }
}

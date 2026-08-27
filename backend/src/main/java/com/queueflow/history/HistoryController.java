package com.queueflow.history;

import com.queueflow.ticket.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final TicketRepository tickets;
    private final TicketService ticketService;

    public record Row(TicketDTO ticket, Double waitMinutes, Double serviceMinutes, Double totalMinutes) {}

    @GetMapping
    @Transactional(readOnly = true)
    public Page<Row> search(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) Long queueId,
            @RequestParam(required = false) Ticket.Status status,
            @RequestParam(required = false) Long attendantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        LocalDate day = date != null ? date : LocalDate.now(ZoneId.systemDefault());
        ZoneId zone = ZoneId.systemDefault();
        Instant from = day.atStartOfDay(zone).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(zone).toInstant();

        Specification<Ticket> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            ps.add(cb.lessThan(root.get("createdAt"), to));
            if (queueId != null) ps.add(cb.equal(root.get("queue").get("id"), queueId));
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            if (attendantId != null) ps.add(cb.equal(root.get("attendant").get("id"), attendantId));
            return cb.and(ps.toArray(new Predicate[0]));
        };

        return tickets.findAll(spec, PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(t -> new Row(ticketService.toDTO(t), minutes(t.getCreatedAt(), t.getCalledAt()),
                        minutes(t.getServiceStartedAt(), t.getFinishedAt()), minutes(t.getCreatedAt(), t.getFinishedAt())));
    }

    private static Double minutes(Instant a, Instant b) {
        if (a == null || b == null) return null;
        double m = (b.toEpochMilli() - a.toEpochMilli()) / 60000.0;
        return Math.round(m * 10) / 10.0;
    }
}

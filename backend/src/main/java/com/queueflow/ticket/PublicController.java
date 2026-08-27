package com.queueflow.ticket;

import com.queueflow.counter.Counter;
import com.queueflow.counter.CounterRepository;
import com.queueflow.common.ApiException;
import com.queueflow.queue.Queue;
import com.queueflow.queue.QueueRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final QueueRepository queues;
    private final TicketService tickets;
    private final TicketRepository ticketRepo;
    private final CounterRepository counters;

    public record IssueReq(@NotNull Long queueId, @NotNull Ticket.PriorityType priorityType) {}

    /** Deploy health check (Render, uptime monitors). */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    /** Active queues for the totem. */
    @GetMapping("/queues")
    public List<Map<String, Object>> activeQueues() {
        return queues.findAll().stream().filter(Queue::isActive)
                .map(q -> Map.<String, Object>of("id", q.getId(), "name", q.getName(), "prefix", q.getPrefix()))
                .toList();
    }

    /** Totem issuance. */
    @PostMapping("/tickets")
    public Map<String, Object> issue(@Valid @RequestBody IssueReq req) {
        Ticket t = tickets.issue(req.queueId(), req.priorityType());
        TicketService.IssuedDTO info = tickets.issuedInfo(t);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("displayCode", info.displayCode());
        resp.put("peopleAhead", info.peopleAhead());
        resp.put("estimatedWaitMinutes", info.estimatedWaitMinutes());
        resp.put("queueName", info.queueName());
        resp.put("createdAt", info.createdAt().toString());
        return resp;
    }

    private record DisplayRow(String code, String counterName) {}

    @GetMapping("/display")
    @Transactional(readOnly = true)
    public Map<String, Object> display() {
        var recent = ticketRepo.findByStatusInOrderByCalledAtDesc(
                List.of(Ticket.Status.CALLED, Ticket.Status.IN_SERVICE, Ticket.Status.FINISHED),
                PageRequest.ofSize(6));
        List<TicketDTO> rows = recent.stream()
                .map(tickets::toDTO)
                .toList();
        long waiting = tickets.waitingCount();
        // single-establishment V1
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("highlight", rows.isEmpty() ? null : rows.get(0));
        result.put("lastCalls", rows.stream().skip(1).limit(5).toList());
        result.put("waiting", waiting);
        return result;
    }
}

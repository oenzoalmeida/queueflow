package com.queueflow.dashboard;

import com.queueflow.counter.CounterRepository;
import com.queueflow.event.TicketEvent;
import com.queueflow.event.TicketEventRepository;
import com.queueflow.ticket.Ticket;
import com.queueflow.ticket.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final TicketRepository tickets;
    private final CounterRepository counters;
    private final TicketEventRepository events;

    public record QueueVolume(String queueName, long issued) {}
    public record HourPoint(int hour, long count) {}
    public record Entry(String displayCode, String queueName, String priorityType, String status,
                        String attendantName, String counterName) {}

    public record DashboardDTO(
            long issuedToday, long finishedToday, long waiting, long absentToday,
            Double avgWaitMinutes, Double avgServiceMinutes, long activeCounters,
            List<HourPoint> byHour, List<QueueVolume> byQueue, List<Entry> lastTickets) {}

    @GetMapping("/today")
    @Transactional(readOnly = true)
    public DashboardDTO today() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate d = LocalDate.now(zone);
        Instant from = d.atStartOfDay(zone).toInstant();
        Instant to = d.plusDays(1).atStartOfDay(zone).toInstant();

        List<Ticket> finishedToday = tickets.finishedBetween(from, to, PageRequest.ofSize(500));
        double avgWait = finishedToday.stream()
                .filter(t -> t.getCalledAt() != null)
                .mapToLong(t -> t.getCalledAt().toEpochMilli() - t.getCreatedAt().toEpochMilli())
                .average().orElse(Double.NaN);
        double avgService = finishedToday.stream()
                .filter(t -> t.getServiceStartedAt() != null)
                .mapToLong(t -> t.getFinishedAt().toEpochMilli() - t.getServiceStartedAt().toEpochMilli())
                .average().orElse(Double.NaN);

        int[] hours = new int[24];
        for (Ticket t : finishedToday)
            hours[t.getFinishedAt().atZone(zone).getHour()]++;

        Map<String, Long> perQueue = new LinkedHashMap<>();
        tickets.findAll().stream().filter(t -> !t.getCreatedAt().isBefore(from) && t.getCreatedAt().isBefore(to))
                .forEach(t -> perQueue.merge(t.getQueue().getName(), 1L, Long::sum));

        var lastRows = tickets.findByStatusInOrderByCalledAtDesc(
                        Arrays.asList(Ticket.Status.values()), PageRequest.ofSize(8));

        return new DashboardDTO(
                tickets.countByCreatedAtBetween(from, to),
                finishedToday.size(),
                tickets.countByStatus(Ticket.Status.WAITING),
                events.countByCreatedAtAfterAndType(from, TicketEvent.Type.ABSENT),
                Double.isNaN(avgWait) ? null : Math.round(avgWait / 60000.0 * 10) / 10.0,
                Double.isNaN(avgService) ? null : Math.round(avgService / 60000.0 * 10) / 10.0,
                counters.countByActiveTrue(),
                hourly(hours),
                volumes(perQueue),
                entries(lastRows));
    }

    private static List<HourPoint> hourly(int[] h) {
        List<HourPoint> list = new ArrayList<>();
        for (int i = 0; i < 24; i++) if (h[i] > 0) list.add(new HourPoint(i, h[i]));
        return list;
    }

    private static List<QueueVolume> volumes(Map<String, Long> m) {
        return m.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5).map(e -> new QueueVolume(e.getKey(), e.getValue())).toList();
    }

    private static List<Entry> entries(List<Ticket> ts) {
        return ts.stream().map(t -> new Entry(
                t.getDisplayCode(),
                t.getQueue() != null ? t.getQueue().getName() : "-",
                t.getPriorityType().name(),
                t.getStatus().name(),
                t.getAttendant() != null ? t.getAttendant().getName() : null,
                t.getCounter() != null ? t.getCounter().getName() : null)).toList();
    }
}

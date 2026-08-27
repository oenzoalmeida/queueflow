package com.queueflow.event;

import com.queueflow.counter.Counter;
import com.queueflow.ticket.Ticket;
import com.queueflow.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "ticket_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TicketEvent {
    public enum Type { CREATED, CALLED, RECALLED, SERVICE_STARTED, FINISHED, ABSENT, CANCELLED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Type type;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "attendant_id")
    private User attendant;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "counter_id")
    private Counter counter;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

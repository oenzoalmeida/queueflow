package com.queueflow.ticket;

import com.queueflow.counter.Counter;
import com.queueflow.queue.Queue;
import com.queueflow.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "tickets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Ticket {
    public enum PriorityType { NORMAL, PRIORITY }
    public enum Status { WAITING, CALLED, IN_SERVICE, FINISHED, ABSENT, CANCELLED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "queue_id", nullable = false)
    private Queue queue;
    @Column(nullable = false)
    private int number;
    @Column(name = "display_code", nullable = false, length = 12)
    private String displayCode;

    @Enumerated(EnumType.STRING) @Column(name = "priority_type", nullable = false, length = 10)
    private PriorityType priorityType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 15)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "called_at")
    private Instant calledAt;
    @Column(name = "service_started_at")
    private Instant serviceStartedAt;
    @Column(name = "finished_at")
    private Instant finishedAt;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "counter_id")
    private Counter counter;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "attendant_id")
    private User attendant;
}

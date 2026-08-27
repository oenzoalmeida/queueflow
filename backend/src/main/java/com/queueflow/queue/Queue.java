package com.queueflow.queue;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "queues")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Queue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 120)
    private String name;
    @Column(nullable = false, unique = true, length = 3)
    private String prefix;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

package com.queueflow.counter;

import com.queueflow.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "counters")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Counter {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 80)
    private String name;
    @Column(nullable = false)
    private boolean active;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "current_attendant_id")
    private User currentAttendant;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

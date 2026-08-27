package com.queueflow.establishment;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "establishments")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Establishment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

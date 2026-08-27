package com.queueflow.settings;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "priority_settings")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PrioritySettings {
    @Id // singleton row (id=1), created by migration
    private Long id;
    @Column(name = "normals_before_priority", nullable = false)
    private int normalsBeforePriority;
}

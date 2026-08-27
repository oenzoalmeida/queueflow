package com.queueflow.ticket;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity @Table(name = "priority_daily_sequences")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PriorityDailySequence {
    @Id @Column(columnDefinition = "date")
    private LocalDate day;
    @Column(name = "last_number")
    private int lastNumber;
}

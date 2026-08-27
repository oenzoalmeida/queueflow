package com.queueflow.ticket;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.LocalDate;

@Entity @Table(name = "daily_sequences")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@IdClass(DailySequence.PK.class)
public class DailySequence {
    @Id @Column(name = "queue_id")
    private Long queueId;
    @Id @Column(columnDefinition = "date")
    private LocalDate day;
    @Column(name = "last_number")
    private int lastNumber;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class PK implements Serializable {
        private Long queueId;
        private LocalDate day;
    }
}

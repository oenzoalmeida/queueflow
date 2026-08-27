package com.queueflow.ticket;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface PriorityDailySequenceRepository extends JpaRepository<PriorityDailySequence, LocalDate> {
    @Modifying
    @Query(value = "INSERT INTO priority_daily_sequences(day, last_number) VALUES (:day, 0) ON CONFLICT DO NOTHING", nativeQuery = true)
    void ensureRow(@Param("day") LocalDate day);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PriorityDailySequence s where s.day = :day")
    PriorityDailySequence lockRow(@Param("day") LocalDate day);
}

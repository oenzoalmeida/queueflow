package com.queueflow.ticket;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DailySequenceRepository extends JpaRepository<DailySequence, DailySequence.PK> {
    @Modifying
    @Query(value = "INSERT INTO daily_sequences(queue_id, day, last_number) VALUES (:queueId, :day, 0) ON CONFLICT DO NOTHING", nativeQuery = true)
    void ensureRow(@Param("queueId") Long queueId, @Param("day") java.time.LocalDate day);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DailySequence s where s.queueId = :queueId and s.day = :day")
    DailySequence lockRow(@Param("queueId") Long queueId, @Param("day") java.time.LocalDate day);
}

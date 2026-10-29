package com.queueflow.counter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface CounterRepository extends JpaRepository<Counter, Long> {
    List<Counter> findAllByOrderByNameAsc();
    Optional<Counter> findByNameIgnoreCase(String name);
    long countByActiveTrue();

    // All ownership changes take the same ordered row locks. This also protects
    // switching counters and simultaneous claims by the same attendant.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Counter c order by c.id")
    List<Counter> findAllForOwnershipUpdate();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Counter c where c.id = :id")
    Optional<Counter> findByIdForUpdate(Long id);
}

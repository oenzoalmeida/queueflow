package com.queueflow.counter;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CounterRepository extends JpaRepository<Counter, Long> {
    List<Counter> findAllByOrderByNameAsc();
    Optional<Counter> findByNameIgnoreCase(String name);
    long countByActiveTrue();
}

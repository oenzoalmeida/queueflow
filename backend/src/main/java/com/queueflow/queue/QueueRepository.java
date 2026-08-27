package com.queueflow.queue;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QueueRepository extends JpaRepository<Queue, Long> {
    boolean existsByPrefixIgnoreCase(String prefix);
    boolean existsByNameIgnoreCase(String name);
    List<Queue> findAllByOrderByNameAsc();
}

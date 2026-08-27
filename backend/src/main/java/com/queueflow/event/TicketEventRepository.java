package com.queueflow.event;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface TicketEventRepository extends JpaRepository<TicketEvent, Long> {
    List<TicketEvent> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
    long countByCreatedAtAfterAndType(Instant after, TicketEvent.Type type);
}

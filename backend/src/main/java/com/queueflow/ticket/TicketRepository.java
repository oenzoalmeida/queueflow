package com.queueflow.ticket;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    /** Locks all waiting tickets (rows stay locked until tx end) so two callers never get the same ticket. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.status = 'WAITING' order by t.createdAt asc, t.id asc")
    List<Ticket> lockWaiting();

    boolean existsByCounterIdAndStatusIn(Long counterId, List<Ticket.Status> statuses);
    Optional<Ticket> findFirstByCounterIdAndStatusInOrderByCalledAtDesc(Long counterId, List<Ticket.Status> statuses);

    long countByStatus(Ticket.Status status);
    long countByCreatedAtBetween(Instant from, Instant to);
    long countByFinishedAtBetween(Instant from, Instant to);

    @Query("select t from Ticket t where t.finishedAt between :from and :to and t.serviceStartedAt is not null order by t.id desc")
    List<Ticket> finishedBetween(@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    List<Ticket> findByStatusInOrderByCalledAtDesc(List<Ticket.Status> statuses, Pageable pageable);

    @Query("select count(t) from Ticket t where t.queue.id = :queueId and t.status = 'WAITING' and t.id < :id")
    long waitingAhead(@Param("queueId") Long queueId, @Param("id") Long id);

    long countByQueueIdAndStatus(Long queueId, Ticket.Status status);
}

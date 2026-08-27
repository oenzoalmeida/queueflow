package com.queueflow.ticket;

import java.time.Instant;

public record TicketDTO(
        Long id,
        String displayCode,
        int number,
        String priorityType,
        String status,
        Long queueId,
        String queueName,
        String counterName,
        String attendantName,
        Instant createdAt,
        Instant calledAt,
        Instant serviceStartedAt,
        Instant finishedAt) {
}

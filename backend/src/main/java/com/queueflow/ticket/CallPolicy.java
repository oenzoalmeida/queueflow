package com.queueflow.ticket;

import java.util.ArrayDeque;
import java.util.Optional;

/**
 * Pure priority policy: after N normal services, prefer a priority ticket.
 * Never stalls: falls back to whichever category is available.
 */
public final class CallPolicy {
    private CallPolicy() {}

    public static Optional<Ticket> pick(ArrayDeque<Ticket> normals, ArrayDeque<Ticket> priorities,
                                        int normalsSincePriority, int normalsBeforePriority) {
        if (normals.isEmpty() && priorities.isEmpty()) return Optional.empty();
        if (priorities.isEmpty()) return Optional.ofNullable(normals.peek());
        if (normals.isEmpty()) return Optional.ofNullable(priorities.peek());
        return normalsSincePriority >= normalsBeforePriority
                ? Optional.ofNullable(priorities.peek())
                : Optional.ofNullable(normals.peek());
    }
}

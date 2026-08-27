package com.queueflow.ticket;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CallPolicyTest {

    private static Ticket t(String code, Ticket.PriorityType p) {
        return Ticket.builder().displayCode(code).priorityType(p).number(1)
                .status(Ticket.Status.WAITING).build();
    }

    @Test
    void patternTwoNormalsThenPriority() {
        ArrayDeque<Ticket> normals = new ArrayDeque<>(List.of(t("A001", Ticket.PriorityType.NORMAL), t("A002", Ticket.PriorityType.NORMAL), t("A003", Ticket.PriorityType.NORMAL)));
        ArrayDeque<Ticket> priorities = new ArrayDeque<>(List.of(t("P001", Ticket.PriorityType.PRIORITY), t("P002", Ticket.PriorityType.PRIORITY)));

        List<String> order = new ArrayList<>();
        int since = 0;
        int N = 2;
        while (!normals.isEmpty() || !priorities.isEmpty()) {
            Optional<Ticket> pick = CallPolicy.pick(normals, priorities, since, N);
            if (pick.isEmpty()) break;
            Ticket chosen = pick.get();
            order.add(chosen.getDisplayCode());
            if (chosen.getPriorityType() == Ticket.PriorityType.PRIORITY) {
                priorities.poll();
                since = 0;
            } else {
                normals.poll();
                since++;
            }
        }
        // N N P N N P
        assertEquals(List.of("A001", "A002", "P001", "A003", "P002"), order);
    }

    @Test
    void fallsBackWhenNoPriorityWaiting() {
        ArrayDeque<Ticket> normals = new ArrayDeque<>(List.of(t("A001", Ticket.PriorityType.NORMAL), t("A002", Ticket.PriorityType.NORMAL)));
        ArrayDeque<Ticket> priorities = new ArrayDeque<>();
        assertEquals("A001", CallPolicy.pick(normals, priorities, 99, 2).orElseThrow().getDisplayCode());
        normals.poll();
        assertEquals("A002", CallPolicy.pick(normals, priorities, 99, 2).orElseThrow().getDisplayCode());
        normals.poll();
        assertTrue(CallPolicy.pick(normals, priorities, 99, 2).isEmpty());
    }

    @Test
    void fallsBackWhenNoNormalWaiting() {
        ArrayDeque<Ticket> normals = new ArrayDeque<>();
        ArrayDeque<Ticket> priorities = new ArrayDeque<>(List.of(t("P001", Ticket.PriorityType.PRIORITY)));
        assertEquals("P001", CallPolicy.pick(normals, priorities, 0, 2).orElseThrow().getDisplayCode());
    }

    @Test
    void emptyQueuesReturnEmpty() {
        assertTrue(CallPolicy.pick(new ArrayDeque<>(), new ArrayDeque<>(), 0, 2).isEmpty());
    }
}

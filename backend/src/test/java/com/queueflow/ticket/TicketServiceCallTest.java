package com.queueflow.ticket;

import com.queueflow.common.ApiException;
import com.queueflow.counter.Counter;
import com.queueflow.counter.CounterRepository;
import com.queueflow.event.TicketEventRepository;
import com.queueflow.queue.Queue;
import com.queueflow.queue.QueueRepository;
import com.queueflow.settings.PrioritySettings;
import com.queueflow.settings.PrioritySettingsRepository;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import com.queueflow.ws.WsPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** Unit tests for call-next (priority order, counter guards) and status transitions. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketServiceCallTest {

    @Mock TicketRepository tickets;
    @Mock DailySequenceRepository sequences;
    @Mock PriorityDailySequenceRepository prioritySequences;
    @Mock QueueRepository queues;
    @Mock CounterRepository counters;
    @Mock UserRepository users;
    @Mock TicketEventRepository events;
    @Mock PrioritySettingsRepository settings;
    @Mock WsPublisher ws;

    TicketService service;
    User attendant;
    Counter counter;
    Queue queueA;
    TicketService.AuthContext ctx;

    @BeforeEach
    void setup() {
        service = new TicketService(tickets, sequences, prioritySequences, queues, counters, users, events, settings, ws);
        attendant = User.builder().id(2L).name("Ana").role(User.Role.ATTENDANT).active(true).build();
        counter = Counter.builder().id(3L).name("Guichê 1").active(true).currentAttendant(attendant).build();
        queueA = Queue.builder().id(7L).name("Atendimento").prefix("A").active(true).build();
        ctx = new TicketService.AuthContext(2L, 3L);

        when(users.findById(2L)).thenReturn(Optional.of(attendant));
        when(counters.findById(3L)).thenReturn(Optional.of(counter));
        when(tickets.existsByCounterIdAndStatusIn(eq(3L), anyList())).thenReturn(false);
        when(settings.get()).thenReturn(PrioritySettings.builder().id(1L).normalsBeforePriority(2).build());
        // no recent calls: normalsSincePriority = 0
        when(tickets.findByStatusInOrderByCalledAtDesc(anyList(), any())).thenReturn(List.of());
        when(tickets.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(events.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Ticket waiting(String code, Ticket.PriorityType p) {
        return Ticket.builder().id(System.nanoTime() % 100000)
                .queue(queueA).number(1).displayCode(code).priorityType(p)
                .status(Ticket.Status.WAITING).createdAt(Instant.now()).build();
    }

    // ---------- callNext: priority order ----------

    @Test
    void callNextPrefersNormalWhenBelowThreshold() {
        when(tickets.lockWaiting()).thenReturn(List.of(waiting("A001", Ticket.PriorityType.NORMAL), waiting("P001", Ticket.PriorityType.PRIORITY)));

        TicketDTO dto = service.callNext(ctx);

        assertEquals("A001", dto.displayCode());
        assertEquals("CALLED", dto.status());
        assertEquals("Guichê 1", dto.counterName());
        verify(ws).display(eq("TICKET_CALLED"), any());
    }

    @Test
    void callNextPrefersPriorityAfterTwoNormals() {
        Ticket calledA1 = waiting("A001", Ticket.PriorityType.NORMAL);
        calledA1.setStatus(Ticket.Status.FINISHED);
        calledA1.setCalledAt(Instant.now());
        Ticket calledA2 = waiting("A002", Ticket.PriorityType.NORMAL);
        calledA2.setStatus(Ticket.Status.FINISHED);
        calledA2.setCalledAt(Instant.now());
        when(tickets.findByStatusInOrderByCalledAtDesc(anyList(), any()))
                .thenReturn(List.of(calledA2, calledA1));
        when(tickets.lockWaiting()).thenReturn(List.of(waiting("A003", Ticket.PriorityType.NORMAL), waiting("P001", Ticket.PriorityType.PRIORITY)));

        TicketDTO dto = service.callNext(ctx);

        assertEquals("P001", dto.displayCode());
        verify(ws).display(eq("TICKET_CALLED"), any());
    }

    @Test
    void callNextRejectsWhenNothingWaiting() {
        when(tickets.lockWaiting()).thenReturn(List.of());
        ApiException.Rule ex = assertThrows(ApiException.Rule.class, () -> service.callNext(ctx));
        assertTrue(ex.getMessage().contains("Nenhuma senha"));
    }

    // ---------- callNext: guards ----------

    @Test
    void callNextRejectedWhileCounterHasActiveTicket() {
        when(tickets.existsByCounterIdAndStatusIn(eq(3L), anyList())).thenReturn(true);
        ApiException.Rule ex = assertThrows(ApiException.Rule.class, () -> service.callNext(ctx));
        assertTrue(ex.getMessage().contains("Finalize"));
        verify(tickets, never()).lockWaiting();
    }

    @Test
    void callNextRejectedForInactiveCounter() {
        counter.setActive(false);
        assertThrows(ApiException.Rule.class, () -> service.callNext(ctx));
    }

    @Test
    void callNextRejectedWhenCounterNotClaimedByAttendant() {
        counter.setCurrentAttendant(User.builder().id(99L).name("Outro").build());
        ApiException.Rule ex = assertThrows(ApiException.Rule.class, () -> service.callNext(ctx));
        assertTrue(ex.getMessage().contains("Selecione este guichê"));
    }

    // ---------- transitions ----------

    private Ticket activeTicket(Ticket.Status status) {
        Ticket t = Ticket.builder().id(42L).queue(queueA).number(1).displayCode("A001")
                .priorityType(Ticket.PriorityType.NORMAL).status(status)
                .createdAt(Instant.now()).calledAt(Instant.now())
                .counter(counter).attendant(attendant).build();
        when(tickets.findFirstByCounterIdAndStatusInOrderByCalledAtDesc(eq(3L), anyList()))
                .thenReturn(Optional.of(t));
        when(tickets.findById(42L)).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    void startMovesCalledToInService() {
        activeTicket(Ticket.Status.CALLED);
        TicketDTO dto = service.start(ctx);
        assertEquals("IN_SERVICE", dto.status());
        assertNotNull(dto.serviceStartedAt());
        verify(ws).display(eq("TICKET_STARTED"), any());
    }

    @Test
    void finishMovesInServiceToFinished() {
        activeTicket(Ticket.Status.IN_SERVICE);
        TicketDTO dto = service.finish(ctx);
        assertEquals("FINISHED", dto.status());
        assertNotNull(dto.finishedAt());
        verify(ws).display(eq("TICKET_FINISHED"), any());
    }

    @Test
    void finishRejectedWhenTicketOnlyCalled() {
        activeTicket(Ticket.Status.CALLED);
        ApiException.Rule ex = assertThrows(ApiException.Rule.class, () -> service.finish(ctx));
        assertTrue(ex.getMessage().contains("estado inválido"));
        verify(ws, never()).display(any(), any());
    }

    @Test
    void markAbsentMovesCalledToAbsent() {
        activeTicket(Ticket.Status.CALLED);
        TicketDTO dto = service.markAbsent(ctx);
        assertEquals("ABSENT", dto.status());
        verify(ws).display(eq("TICKET_ABSENT"), any());
    }

    @Test
    void recallKeepsStatusCalled() {
        activeTicket(Ticket.Status.CALLED);
        TicketDTO dto = service.recall(ctx);
        assertEquals("CALLED", dto.status());
        verify(ws).display(eq("TICKET_RECALLED"), any());
    }

    @Test
    void cancelRejectedForFinishedTicket() {
        Ticket t = activeTicket(Ticket.Status.FINISHED);
        ApiException.Rule ex = assertThrows(ApiException.Rule.class, () -> service.cancel(t.getId()));
        assertTrue(ex.getMessage().contains("cancelar"));
    }
}

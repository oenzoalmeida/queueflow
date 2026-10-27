package com.queueflow.ticket;

import com.queueflow.common.ApiException;
import com.queueflow.counter.CounterRepository;
import com.queueflow.event.TicketEvent;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Unit tests for ticket issuance: per-queue daily sequence, daily reset, priority sequence and validation. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketServiceIssueTest {

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
    Queue queueA;

    @BeforeEach
    void setup() {
        service = new TicketService(tickets, sequences, prioritySequences, queues, counters, users, events, settings, ws);
        queueA = Queue.builder().id(7L).name("Atendimento").prefix("A").active(true).build();
        when(queues.findById(7L)).thenReturn(Optional.of(queueA));
        when(tickets.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(events.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void normalTicketUsesQueuePrefixAndIncrementsSequence() {
        when(sequences.lockRow(7L, LocalDate.now())).thenReturn(seq(7L, LocalDate.now(), 4));

        Ticket t = service.issue(7L, Ticket.PriorityType.NORMAL);

        assertEquals(5, t.getNumber());
        assertEquals("A005", t.getDisplayCode());
        assertEquals(Ticket.Status.WAITING, t.getStatus());
        assertEquals(Ticket.PriorityType.NORMAL, t.getPriorityType());
        verify(sequences).ensureRow(7L, LocalDate.now());
        verify(prioritySequences, never()).ensureRow(any());
    }

    @Test
    void firstTicketOfTheDayStartsAtOne() {
        // a fresh row for a new day means the previous day's counter is irrelevant (daily reset)
        when(sequences.lockRow(7L, LocalDate.now())).thenReturn(seq(7L, LocalDate.now(), 0));

        Ticket t = service.issue(7L, Ticket.PriorityType.NORMAL);

        assertEquals(1, t.getNumber());
        assertEquals("A001", t.getDisplayCode());
    }

    @Test
    void sequencesArePerQueue() {
        Queue queueB = Queue.builder().id(8L).name("Exames").prefix("B").active(true).build();
        when(queues.findById(8L)).thenReturn(Optional.of(queueB));
        when(sequences.lockRow(8L, LocalDate.now())).thenReturn(seq(8L, LocalDate.now(), 9));

        Ticket t = service.issue(8L, Ticket.PriorityType.NORMAL);

        assertEquals("B010", t.getDisplayCode());
        verify(sequences).lockRow(8L, LocalDate.now());
    }

    @Test
    void priorityTicketUsesSharedEstablishmentWideSequence() {
        when(prioritySequences.lockRow(LocalDate.now())).thenReturn(prioritySeq(LocalDate.now(), 2));

        Ticket t = service.issue(7L, Ticket.PriorityType.PRIORITY);

        assertEquals(3, t.getNumber());
        assertEquals("P003", t.getDisplayCode());
        verify(prioritySequences).ensureRow(LocalDate.now());
        // priority numbering must NOT consume the queue's normal sequence
        verify(sequences, never()).lockRow(any(), any());
    }

    @Test
    void inactiveQueueIsRejected() {
        queueA.setActive(false);
        ApiException.Rule ex = assertThrows(ApiException.Rule.class,
                () -> service.issue(7L, Ticket.PriorityType.NORMAL));
        assertTrue(ex.getMessage().contains("indispon"));
        verify(tickets, never()).save(any());
    }

    @Test
    void unknownQueueIsRejected() {
        ApiException ex = assertThrows(ApiException.NotFound.class,
                () -> service.issue(99L, Ticket.PriorityType.NORMAL));
        assertTrue(ex.getMessage().contains("Fila"));
    }

    @Test
    void issuancePublishesWsEventAndAuditEvent() {
        when(sequences.lockRow(7L, LocalDate.now())).thenReturn(seq(7L, LocalDate.now(), 0));

        service.issue(7L, Ticket.PriorityType.NORMAL);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(ws).display(eq("TICKET_ISSUED"), payload.capture());
        assertInstanceOf(TicketDTO.class, payload.getValue());

        ArgumentCaptor<TicketEvent> event = ArgumentCaptor.forClass(TicketEvent.class);
        verify(events).save(event.capture());
        assertEquals(TicketEvent.Type.CREATED, event.getValue().getType());
        assertNull(event.getValue().getAttendant());
    }

    private DailySequence seq(long queueId, LocalDate day, int last) {
        DailySequence s = new DailySequence(queueId, day, last);
        return s;
    }

    private PriorityDailySequence prioritySeq(LocalDate day, int last) {
        return new PriorityDailySequence(day, last);
    }
}

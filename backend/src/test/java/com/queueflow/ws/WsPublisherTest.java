package com.queueflow.ws;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Unit tests for the WebSocket publisher: message shape and after-commit delivery semantics. */
@ExtendWith(MockitoExtension.class)
class WsPublisherTest {

    @Mock SimpMessagingTemplate template;

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void displaySendsTypedEnvelopeImmediatelyWithoutTransaction() {
        WsPublisher publisher = new WsPublisher(template);

        publisher.display("TICKET_ISSUED", Map.of("displayCode", "A001"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> msg = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(template).convertAndSend(eq("/topic/display"), msg.capture());
        Map<String, Object> envelope = msg.getValue();
        assertEquals("TICKET_ISSUED", envelope.get("type"));
        assertEquals(Map.of("displayCode", "A001"), envelope.get("payload"));
    }

    @Test
    void attendantSendsToAttendantTopic() {
        new WsPublisher(template).attendant(Map.of("waiting", 3));
        verify(template).convertAndSend(eq("/topic/attendant"), anyMap());
    }

    @Test
    void messageDeferredUntilAfterCommitWhenTransactionActive() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        WsPublisher publisher = new WsPublisher(template);

        publisher.display("TICKET_CALLED", Map.of("ticket", "P001"));

        // registered but NOT delivered yet
        verify(template, never()).convertAndSend(anyString(), anyMap());
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, syncs.size());

        syncs.get(0).afterCommit();
        verify(template).convertAndSend(eq("/topic/display"), anyMap());
    }

    @Test
    void wsFailureNeverPropagates() {
        doThrow(new IllegalStateException("broker down")).when(template).convertAndSend(anyString(), anyMap());
        assertDoesNotThrow(() -> new WsPublisher(template).display("TICKET_FINISHED", Map.of()));
    }
}

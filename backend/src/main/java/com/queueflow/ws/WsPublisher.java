package com.queueflow.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/** Broadcasts events to WebSocket topics only after the enclosing transaction commits. */
@Component
@RequiredArgsConstructor
public class WsPublisher {
    private final SimpMessagingTemplate template;

    public void display(String type, Object payload) {
        send("/topic/display", Map.of("type", type, "payload", payload));
    }

    public void attendant(Object payload) {
        send("/topic/attendant", payload);
    }

    private void send(String topic, Object message) {
        Runnable push = () -> {
            try {
                template.convertAndSend(topic, message);
            } catch (Exception e) {
                // never fail a committed business transaction because of WS delivery
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                @Override
                public void afterCommit() { push.run(); }
            });
        } else {
            push.run();
        }
    }
}

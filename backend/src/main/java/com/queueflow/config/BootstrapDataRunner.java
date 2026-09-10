package com.queueflow.config;

import com.queueflow.counter.Counter;
import com.queueflow.counter.CounterRepository;
import com.queueflow.establishment.Establishment;
import com.queueflow.establishment.EstablishmentRepository;
import com.queueflow.queue.Queue;
import com.queueflow.queue.QueueRepository;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * One-time, opt-in bootstrap for a fresh production/demo database.
 * Credentials are supplied only through environment variables and are never committed.
 * Disable QUEUEFLOW_BOOTSTRAP_ENABLED after the first successful start.
 */
@Component
@ConditionalOnProperty(name = "queueflow.bootstrap.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class BootstrapDataRunner implements CommandLineRunner {

    private final UserRepository users;
    private final EstablishmentRepository establishments;
    private final QueueRepository queues;
    private final CounterRepository counters;
    private final PasswordEncoder encoder;

    @Value("${queueflow.bootstrap.admin-email:}")
    private String adminEmail;

    @Value("${queueflow.bootstrap.admin-password:}")
    private String adminPassword;

    @Value("${queueflow.bootstrap.attendant-email:}")
    private String attendantEmail;

    @Value("${queueflow.bootstrap.attendant-password:}")
    private String attendantPassword;

    @Override
    public void run(String... args) {
        validateCredentials();

        users.findByEmailIgnoreCase(adminEmail).orElseGet(() ->
                users.save(User.builder()
                        .name("Admin Demo")
                        .email(adminEmail.trim().toLowerCase())
                        .passwordHash(encoder.encode(adminPassword))
                        .role(User.Role.ADMIN)
                        .active(true)
                        .createdAt(Instant.now())
                        .build()));

        users.findByEmailIgnoreCase(attendantEmail).orElseGet(() ->
                users.save(User.builder()
                        .name("Atendente Demo")
                        .email(attendantEmail.trim().toLowerCase())
                        .passwordHash(encoder.encode(attendantPassword))
                        .role(User.Role.ATTENDANT)
                        .active(true)
                        .createdAt(Instant.now())
                        .build()));

        if (establishments.count() == 0) {
            establishments.save(Establishment.builder()
                    .name("QueueFlow Demo")
                    .createdAt(Instant.now())
                    .build());
        }

        if (queues.count() == 0) {
            queues.save(Queue.builder().name("Atendimento Geral").prefix("A").active(true).createdAt(Instant.now()).build());
            queues.save(Queue.builder().name("Financeiro").prefix("F").active(true).createdAt(Instant.now()).build());
        }

        if (counters.count() == 0) {
            counters.save(Counter.builder().name("Guichê 01").active(true).createdAt(Instant.now()).build());
            counters.save(Counter.builder().name("Guichê 02").active(true).createdAt(Instant.now()).build());
        }

        log.info("QueueFlow bootstrap completed successfully");
    }

    private void validateCredentials() {
        if (adminEmail == null || adminEmail.isBlank()
                || attendantEmail == null || attendantEmail.isBlank()
                || adminPassword == null || adminPassword.length() < 12
                || attendantPassword == null || attendantPassword.length() < 12) {
            throw new IllegalStateException("QueueFlow bootstrap credentials are missing or too short");
        }
        if (adminEmail.equalsIgnoreCase(attendantEmail)) {
            throw new IllegalStateException("QueueFlow bootstrap emails must be different");
        }
    }
}

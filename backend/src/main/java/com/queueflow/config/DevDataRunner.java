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
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** DEV-only seed: admin + demo queues/counters when the database is empty. */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DevDataRunner implements CommandLineRunner {

    private final UserRepository users;
    private final EstablishmentRepository establishments;
    private final QueueRepository queues;
    private final CounterRepository counters;
    private final PasswordEncoder encoder;

    @Override
    public void run(String... args) {
        if (users.count() > 0) return;
        establishments.save(Establishment.builder().name("Clínica QueueFlow").createdAt(Instant.now()).build());
        users.save(User.builder().name("Admin Demo").email("admin@queueflow.local")
                .passwordHash(encoder.encode("Admin123!")).role(User.Role.ADMIN).active(true)
                .createdAt(Instant.now()).build());
        users.save(User.builder().name("Ana Atendente").email("ana@queueflow.local")
                .passwordHash(encoder.encode("Atend123!")).role(User.Role.ATTENDANT).active(true)
                .createdAt(Instant.now()).build());
        queues.save(Queue.builder().name("Atendimento Geral").prefix("A").active(true).createdAt(Instant.now()).build());
        queues.save(Queue.builder().name("Financeiro").prefix("F").active(true).createdAt(Instant.now()).build());
        counters.save(Counter.builder().name("Guichê 01").active(true).createdAt(Instant.now()).build());
        counters.save(Counter.builder().name("Guichê 02").active(true).createdAt(Instant.now()).build());
        log.info("DEV data seeded: admin@queueflow.local / Admin123!");
    }
}

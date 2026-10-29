package com.queueflow.counter;

import com.queueflow.common.ApiException;
import com.queueflow.config.JwtAuthFilter.AuthUser;
import com.queueflow.user.User;
import com.queueflow.user.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CounterClaimIT {
    @Autowired CounterController controller;
    @Autowired CounterRepository counters;
    @Autowired UserRepository users;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    User first, second;
    Counter a, b;

    @BeforeEach void setup() {
        String tag = UUID.randomUUID().toString();
        first = users.save(User.builder().name("First").email(tag + "a@test.local")
                .passwordHash("not-a-real-login").role(User.Role.ATTENDANT).active(true).createdAt(Instant.now()).build());
        second = users.save(User.builder().name("Second").email(tag + "b@test.local")
                .passwordHash("not-a-real-login").role(User.Role.ATTENDANT).active(true).createdAt(Instant.now()).build());
        a = counters.save(Counter.builder().name(tag + " A").active(true).createdAt(Instant.now()).build());
        b = counters.save(Counter.builder().name(tag + " B").active(true).createdAt(Instant.now()).build());
    }
    @AfterEach void cleanup() {
        counters.deleteAllById(List.of(a.getId(), b.getId()));
        users.deleteAllById(List.of(first.getId(), second.getId()));
    }
    AuthUser auth(User u) { return new AuthUser(u.getId(), u.getName(), u.getEmail(), u.getRole()); }
    boolean claim(Long counter, User user) {
        try { controller.claim(counter, auth(user)); return true; }
        catch (ApiException.Rule e) { return false; }
    }

    /** A separate transaction holds rows until BOTH requests are waiting in PostgreSQL.
     * With the old read/modify/write code both read 'free' before their writes block.
     * With locking they wait before reading ownership. No timing-only race assertion. */
    List<Boolean> concurrentClaims(Long one, User u1, Long two, User u2) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.createStatement()) {
                statement.executeQuery("select id from counters order by id for update").close();
            }
            Future<Boolean> left = pool.submit(() -> claim(one, u1));
            Future<Boolean> right = pool.submit(() -> claim(two, u2));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            boolean bothWaiting = false;
            while (System.nanoTime() < deadline) {
                Integer waiting = jdbc.queryForObject("select count(*) from pg_stat_activity "
                        + "where datname=current_database() and wait_event_type='Lock'", Integer.class);
                if (waiting != null && waiting >= 2) { bothWaiting = true; break; }
                Thread.sleep(20);
            }
            blocker.commit();
            assertTrue(bothWaiting, "both requests must reach their DB lock before release");
            return List.of(left.get(15, TimeUnit.SECONDS), right.get(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));
        }
    }

    @Test void onlyOneAttendantCanClaimTheSameFreeCounter() throws Exception {
        List<Boolean> results = concurrentClaims(a.getId(), first, a.getId(), second);
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        Long owner = jdbc.queryForObject("select current_attendant_id from counters where id=?", Long.class, a.getId());
        assertEquals(results.get(0) ? first.getId() : second.getId(), owner);
    }

    @Test void sameAttendantCannotEndUpOwningTwoCounters() throws Exception {
        assertEquals(List.of(true, true), concurrentClaims(a.getId(), first, b.getId(), first));
        assertEquals(1, jdbc.queryForObject("select count(*) from counters where current_attendant_id=?", Integer.class, first.getId()));
    }

    @Test void occupiedTargetDoesNotReleaseThePreviousCounter() {
        assertTrue(claim(a.getId(), first));
        assertTrue(claim(b.getId(), second));
        assertFalse(claim(b.getId(), first));
        assertEquals(first.getId(), jdbc.queryForObject("select current_attendant_id from counters where id=?", Long.class, a.getId()));
    }

    @Test void reclaimIsIdempotentAndReleaseClearsOwnership() {
        assertTrue(claim(a.getId(), first));
        assertTrue(claim(a.getId(), first));
        controller.release(auth(first));
        assertEquals(0, jdbc.queryForObject("select count(*) from counters where current_attendant_id=?", Integer.class, first.getId()));
    }
}

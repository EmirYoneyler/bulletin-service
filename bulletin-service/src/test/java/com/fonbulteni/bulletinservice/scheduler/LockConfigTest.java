package com.fonbulteni.bulletinservice.scheduler;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// Locks must be committed to be visible; the default per-test rollback would hide them.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(LockConfig.class)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class LockConfigTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    LockProvider lockProvider;

    @Autowired
    LockingTaskExecutor executor;

    @Autowired
    JdbcTemplate jdbc;

    private static LockConfiguration lock(String name) {
        return new LockConfiguration(Instant.now(), name, Duration.ofMinutes(5), Duration.ZERO);
    }

    @Test
    void taskRunsWhenLockIsFree() throws Throwable {
        LockingTaskExecutor.TaskResult<String> result = executor.executeWithLock(() -> "ran", lock("free-lock"));

        assertThat(result.wasExecuted()).isTrue();
        assertThat(result.getResult()).isEqualTo("ran");
        assertThat(jdbc.queryForObject("select count(*) from shedlock where name = 'free-lock'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void taskIsSkippedWhileAnotherHolderHasTheLock() throws Throwable {
        // Simulates another instance (or the scheduled run) holding the lock.
        SimpleLock held = lockProvider.lock(lock("busy-lock")).orElseThrow();
        try {
            LockingTaskExecutor.TaskResult<String> result = executor.executeWithLock(() -> "ran", lock("busy-lock"));
            assertThat(result.wasExecuted()).isFalse();
        } finally {
            held.unlock();
        }

        assertThat(executor.executeWithLock(() -> "ran", lock("busy-lock")).wasExecuted()).isTrue();
    }
}

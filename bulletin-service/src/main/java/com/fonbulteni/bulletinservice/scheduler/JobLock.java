package com.fonbulteni.bulletinservice.scheduler;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/** Runs a task under a named ShedLock lock; empty if someone else holds it. Shared by all jobs. */
@Component
public class JobLock {

    private final LockingTaskExecutor lockingTaskExecutor;
    private final Clock clock;

    public JobLock(LockingTaskExecutor lockingTaskExecutor, Clock clock) {
        this.lockingTaskExecutor = lockingTaskExecutor;
        this.clock = clock;
    }

    public <T> Optional<T> runExclusive(String lockName, Duration lockAtMostFor,
                                        LockingTaskExecutor.TaskWithResult<T> task) {
        LockConfiguration lock = new LockConfiguration(clock.instant(), lockName, lockAtMostFor, Duration.ZERO);
        try {
            LockingTaskExecutor.TaskResult<T> result = lockingTaskExecutor.executeWithLock(task, lock);
            return result.wasExecuted() ? Optional.ofNullable(result.getResult()) : Optional.empty();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }
}

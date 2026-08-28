package com.oj.runner.service;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Keeps an unavailable Docker control plane from pinning Runner HTTP threads. */
@Component
public class BoundedReadinessProbe implements AutoCloseable {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(750);

    private final Duration timeout;
    private final ThreadPoolExecutor executor;

    public BoundedReadinessProbe() {
        this(DEFAULT_TIMEOUT);
    }

    public BoundedReadinessProbe(Duration timeout) {
        this.timeout = timeout;
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(task, "runner-readiness-probe-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1),
                threadFactory, new ThreadPoolExecutor.AbortPolicy());
    }

    public boolean check(BooleanSupplier dependencyCheck) {
        Future<Boolean> future = null;
        try {
            future = executor.submit(dependencyCheck::getAsBoolean);
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | RejectedExecutionException | TimeoutException exception) {
            return false;
        } finally {
            if (future != null && !future.isDone()) {
                future.cancel(true);
                executor.purge();
            }
        }
    }

    @PreDestroy
    @Override
    public void close() {
        executor.shutdownNow();
    }
}

package com.oj.observability;

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

/** A separate, bounded executor keeps an admin observation request from pinning HTTP threads. */
@Component
public class BoundedStatusProbe implements AutoCloseable {
    private static final Duration TIMEOUT = Duration.ofMillis(750);
    private final ThreadPoolExecutor executor;

    public BoundedStatusProbe() {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "admin-status-probe-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), factory, new ThreadPoolExecutor.AbortPolicy());
    }

    public ProbeResult check(BooleanSupplier check) {
        long started = System.nanoTime();
        Future<Boolean> future = null;
        try {
            future = executor.submit(check::getAsBoolean);
            return new ProbeResult(Boolean.TRUE.equals(future.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)), elapsed(started));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ProbeResult(false, elapsed(started));
        } catch (ExecutionException | RejectedExecutionException | TimeoutException exception) {
            return new ProbeResult(false, elapsed(started));
        } finally {
            if (future != null && !future.isDone()) future.cancel(true);
        }
    }

    private static long elapsed(long started) { return (System.nanoTime() - started) / 1_000_000; }

    @PreDestroy
    @Override
    public void close() { executor.shutdownNow(); }

    public record ProbeResult(boolean up, long latencyMs) {}
}

package com.oj.office;

import com.oj.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class OfficeJudgeConcurrencyGate {

    private final Semaphore permits;
    private final int maximum;
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger peak = new AtomicInteger();

    public OfficeJudgeConcurrencyGate(AppProperties properties) {
        this.maximum = Math.max(1, properties.getOffice().getMaxConcurrentJudges());
        this.permits = new Semaphore(maximum, true);
    }

    public Permit acquire() {
        permits.acquireUninterruptibly();
        int current = active.incrementAndGet();
        peak.accumulateAndGet(current, Math::max);
        return new Permit();
    }

    int maximum() {
        return maximum;
    }

    int peakObserved() {
        return peak.get();
    }

    public final class Permit implements AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                active.decrementAndGet();
                permits.release();
            }
        }
    }
}

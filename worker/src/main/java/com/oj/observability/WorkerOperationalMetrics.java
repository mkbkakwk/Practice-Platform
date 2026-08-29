package com.oj.observability;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/** Small in-process evidence counters, available only on the internal Worker port. */
@Component
public class WorkerOperationalMetrics {
    private final LongAdder received = new LongAdder();
    private final LongAdder completed = new LongAdder();
    private final LongAdder failed = new LongAdder();
    private final LongAdder retries = new LongAdder();

    public void received() { received.increment(); }
    public void completed() { completed.increment(); }
    public void failed() { failed.increment(); }
    public void retry() { retries.increment(); }
    public Map<String, Long> snapshot() {
        return Map.of("received", received.sum(), "completed", completed.sum(),
                "failed", failed.sum(), "retries", retries.sum());
    }
}

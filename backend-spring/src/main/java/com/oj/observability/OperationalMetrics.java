package com.oj.observability;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/** Small in-process counters for operational evidence; no metrics backend is required. */
@Component
public class OperationalMetrics {
    private static final LongAdder HTTP_REQUESTS = new LongAdder();
    private static final LongAdder HTTP_LATENCY_NANOS = new LongAdder();
    private final LongAdder submissionsAccepted = new LongAdder();
    private final LongAdder outboxPublished = new LongAdder();
    private final LongAdder outboxPublishFailures = new LongAdder();

    public static void recordHttp(long elapsedNanos) {
        HTTP_REQUESTS.increment();
        HTTP_LATENCY_NANOS.add(Math.max(0, elapsedNanos));
    }

    public void submissionAccepted() { submissionsAccepted.increment(); }
    public void outboxPublished() { outboxPublished.increment(); }
    public void outboxPublishFailure() { outboxPublishFailures.increment(); }

    public Map<String, Long> snapshot() {
        long requests = HTTP_REQUESTS.sum();
        return Map.of(
                "httpRequests", requests,
                "httpAverageLatencyMs", requests == 0 ? 0 : HTTP_LATENCY_NANOS.sum() / requests / 1_000_000,
                "submissionsAccepted", submissionsAccepted.sum(),
                "outboxPublished", outboxPublished.sum(),
                "outboxPublishFailures", outboxPublishFailures.sum());
    }
}

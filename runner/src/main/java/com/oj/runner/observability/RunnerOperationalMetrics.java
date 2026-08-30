package com.oj.runner.observability;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/** Small in-process Runner execution evidence; no external metrics service is required. */
@Component
public class RunnerOperationalMetrics {
    private final LongAdder executions = new LongAdder();
    private final LongAdder failures = new LongAdder();

    public void execution() { executions.increment(); }
    public void failure() { failures.increment(); }
    public Map<String, Long> snapshot() { return Map.of("executions", executions.sum(), "failures", failures.sum()); }
}

package com.oj.observability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedStatusProbeTest {
    @Test
    void returnsDownBeforeABlockingDependencyCanPinTheCaller() {
        try (BoundedStatusProbe probe = new BoundedStatusProbe()) {
            long started = System.nanoTime();
            var result = probe.check(() -> {
                try { Thread.sleep(5_000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                return true;
            });
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            assertFalse(result.up());
            assertTrue(elapsedMs < 1_500, "status probe must leave room below the three second acceptance budget");
            assertTrue(probe.check(() -> true).up(), "a cancelled probe must not exhaust the bounded executor");
        }
    }
}

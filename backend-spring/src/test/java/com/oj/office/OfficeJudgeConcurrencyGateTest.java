package com.oj.office;

import com.oj.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class OfficeJudgeConcurrencyGateTest {

    @Test
    void twentyJudgesNeverExceedConfiguredConcurrency() throws Exception {
        AppProperties properties = new AppProperties();
        properties.getOffice().setMaxConcurrentJudges(4);
        OfficeJudgeConcurrencyGate gate = new OfficeJudgeConcurrencyGate(properties);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(20)) {
            List<Callable<Void>> tasks = IntStream.range(0, 20)
                    .mapToObj(index -> (Callable<Void>) () -> {
                        start.await();
                        try (OfficeJudgeConcurrencyGate.Permit ignored = gate.acquire()) {
                            int current = active.incrementAndGet();
                            peak.accumulateAndGet(current, Math::max);
                            Thread.sleep(10);
                            active.decrementAndGet();
                        }
                        return null;
                    }).toList();
            var futures = tasks.stream().map(executor::submit).toList();
            start.countDown();
            for (var future : futures) future.get(10, TimeUnit.SECONDS);
        }

        assertThat(gate.maximum()).isEqualTo(4);
        assertThat(gate.peakObserved()).isEqualTo(4);
        assertThat(peak).hasValue(4);
        assertThat(active).hasValue(0);
    }
}

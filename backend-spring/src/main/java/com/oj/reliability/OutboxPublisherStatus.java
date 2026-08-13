package com.oj.reliability;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class OutboxPublisherStatus {
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<Instant> lastPoll = new AtomicReference<>();
    private final AtomicReference<String> lastFailure = new AtomicReference<>();

    void pollStarted() {
        running.set(true);
        lastPoll.set(Instant.now());
    }

    void pollFinished() {
        running.set(false);
    }

    void confirmed() {
        lastFailure.set(null);
    }

    void failed(String category) {
        lastFailure.set(category);
    }

    public boolean isRunning() { return running.get(); }
    public Instant getLastPoll() { return lastPoll.get(); }
    public String getLastFailure() { return lastFailure.get(); }
}

package com.oj.controller;

import com.oj.reliability.JudgeOutboxRepository;
import com.oj.reliability.OutboxPublisherStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final JudgeOutboxRepository outboxRepository;
    private final OutboxPublisherStatus publisherStatus;

    public HealthController(JudgeOutboxRepository outboxRepository, OutboxPublisherStatus publisherStatus) {
        this.outboxRepository = outboxRepository;
        this.publisherStatus = publisherStatus;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "ok", true,
                "ts", System.currentTimeMillis(),
                "judgeMessaging", Map.of(
                        "outboxPending", outboxRepository.pendingCount(),
                        "publisherRunning", publisherStatus.isRunning(),
                        "lastPublishFailure", publisherStatus.getLastFailure() == null
                                ? "" : publisherStatus.getLastFailure()));
    }
}

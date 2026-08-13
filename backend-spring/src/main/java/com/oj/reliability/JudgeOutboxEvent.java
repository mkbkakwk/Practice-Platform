package com.oj.reliability;

import java.util.UUID;

public record JudgeOutboxEvent(
        long id,
        UUID eventId,
        int submissionId,
        String payload,
        int attemptCount,
        UUID publisherToken) {
}

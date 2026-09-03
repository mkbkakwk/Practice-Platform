package com.oj.reliability;

import java.util.UUID;

public record JudgeDeadLetter(
        UUID eventId,
        Integer submissionId,
        String originalRoutingKey,
        int attemptCount,
        String failureCategory,
        String failedAt) {
}

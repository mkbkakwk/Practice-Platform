package com.oj.reliability;

import java.util.UUID;

public record JudgeMessage(UUID eventId, int submissionId, int schemaVersion, int deliveryAttempt) {
    public static JudgeMessage initial(UUID eventId, int submissionId) {
        return new JudgeMessage(eventId, submissionId, 1, 0);
    }
}

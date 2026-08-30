package com.oj.reliability;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record JudgeMessage(UUID eventId, int submissionId, int judgeGeneration,
                           int schemaVersion, int deliveryAttempt, String requestId) {
    public JudgeMessage(UUID eventId, int submissionId, int judgeGeneration,
                        int schemaVersion, int deliveryAttempt) {
        this(eventId, submissionId, judgeGeneration, schemaVersion, deliveryAttempt, null);
    }

    public static JudgeMessage initial(int submissionId, int judgeGeneration) {
        return initial(submissionId, judgeGeneration, null);
    }

    public static JudgeMessage initial(int submissionId, int judgeGeneration, String requestId) {
        UUID eventId = UUID.nameUUIDFromBytes(
                ("judge-request:" + submissionId + ":" + judgeGeneration)
                        .getBytes(StandardCharsets.UTF_8));
        return new JudgeMessage(eventId, submissionId, judgeGeneration, 1, 0, requestId);
    }
}

package com.oj.reliability;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record JudgeMessage(UUID eventId, int submissionId, int judgeGeneration,
                           int schemaVersion, int deliveryAttempt) {
    public static JudgeMessage initial(int submissionId, int judgeGeneration) {
        UUID eventId = UUID.nameUUIDFromBytes(
                ("judge-request:" + submissionId + ":" + judgeGeneration)
                        .getBytes(StandardCharsets.UTF_8));
        return new JudgeMessage(eventId, submissionId, judgeGeneration, 1, 0);
    }
}

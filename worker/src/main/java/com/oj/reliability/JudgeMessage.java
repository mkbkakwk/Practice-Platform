package com.oj.reliability;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

public record JudgeMessage(UUID eventId, int submissionId, int schemaVersion, int deliveryAttempt) {

    public static JudgeMessage from(Map<String, Object> payload) {
        Object submissionValue = payload.get("submissionId");
        if (!(submissionValue instanceof Number number)) {
            throw new IllegalArgumentException("submissionId is required");
        }
        int submissionId = number.intValue();
        if (submissionId <= 0) {
            throw new IllegalArgumentException("submissionId is invalid");
        }

        UUID eventId;
        Object eventValue = payload.get("eventId");
        if (eventValue == null) {
            eventId = UUID.nameUUIDFromBytes(
                    ("legacy-judge-event:" + submissionId).getBytes(StandardCharsets.UTF_8));
        } else {
            eventId = UUID.fromString(eventValue.toString());
        }
        int schemaVersion = number(payload.get("schemaVersion"), 1);
        int deliveryAttempt = number(payload.get("deliveryAttempt"), 0);
        if (schemaVersion != 1 || deliveryAttempt < 0) {
            throw new IllegalArgumentException("judge message version or attempt is invalid");
        }
        return new JudgeMessage(eventId, submissionId, schemaVersion, deliveryAttempt);
    }

    public JudgeMessage retry(int nextAttempt) {
        return new JudgeMessage(eventId, submissionId, schemaVersion, nextAttempt);
    }

    private static int number(Object value, int fallback) {
        return value == null ? fallback : ((Number) value).intValue();
    }
}

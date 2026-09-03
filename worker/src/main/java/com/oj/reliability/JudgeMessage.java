package com.oj.reliability;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public record JudgeMessage(UUID eventId, int submissionId, int judgeGeneration,
                           int schemaVersion, int deliveryAttempt, String requestId) {
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    public JudgeMessage(UUID eventId, int submissionId, int judgeGeneration,
                        int schemaVersion, int deliveryAttempt) {
        this(eventId, submissionId, judgeGeneration, schemaVersion, deliveryAttempt, null);
    }

    /** Legacy in-process constructor: messages written before V9 are generation zero. */
    public JudgeMessage(UUID eventId, int submissionId, int schemaVersion, int deliveryAttempt) {
        this(eventId, submissionId, 0, schemaVersion, deliveryAttempt, null);
    }

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
        int judgeGeneration = number(payload.get("judgeGeneration"), 0);
        int schemaVersion = number(payload.get("schemaVersion"), 1);
        int deliveryAttempt = number(payload.get("deliveryAttempt"), 0);
        if (judgeGeneration < 0 || schemaVersion != 1 || deliveryAttempt < 0) {
            throw new IllegalArgumentException("judge message version or attempt is invalid");
        }
        Object requestIdValue = payload.get("requestId");
        String requestId = requestIdValue == null ? null : requestIdValue.toString();
        if (requestId != null && !SAFE_REQUEST_ID.matcher(requestId).matches()) {
            throw new IllegalArgumentException("requestId is invalid");
        }
        return new JudgeMessage(eventId, submissionId, judgeGeneration, schemaVersion, deliveryAttempt, requestId);
    }

    public JudgeMessage retry(int nextAttempt) {
        return new JudgeMessage(eventId, submissionId, judgeGeneration, schemaVersion, nextAttempt, requestId);
    }

    private static int number(Object value, int fallback) {
        return value == null ? fallback : ((Number) value).intValue();
    }
}

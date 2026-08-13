package com.oj.reliability;

import java.util.UUID;

public record JudgeClaim(Status status, UUID judgeToken, JudgeWork work) {
    public enum Status { CLAIMED, BUSY, FINAL, NOT_FOUND }

    public static JudgeClaim claimed(UUID token, JudgeWork work) {
        return new JudgeClaim(Status.CLAIMED, token, work);
    }

    public static JudgeClaim of(Status status) {
        return new JudgeClaim(status, null, null);
    }
}

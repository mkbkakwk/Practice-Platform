package com.oj.reliability;

public record JudgeWork(
        int submissionId,
        int userId,
        String language,
        String code,
        long timeLimitMs,
        long memoryLimitKb,
        String testCasesJson) {
}

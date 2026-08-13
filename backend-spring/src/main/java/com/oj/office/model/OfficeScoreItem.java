package com.oj.office.model;

public record OfficeScoreItem(
        String ruleId,
        String target,
        String expected,
        String actual,
        int score,
        int earned,
        boolean passed,
        String message) {
}

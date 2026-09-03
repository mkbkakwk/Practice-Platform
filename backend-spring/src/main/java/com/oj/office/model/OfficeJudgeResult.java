package com.oj.office.model;

import java.util.List;

public record OfficeJudgeResult(
        String judgeVersion,
        int totalScore,
        int earnedScore,
        boolean passed,
        List<OfficeScoreItem> items,
        int totalErrorCount,
        boolean truncated,
        List<OfficeComparisonRow> comparisonRows) {

    public OfficeJudgeResult {
        if (totalScore < 0 || earnedScore < 0 || earnedScore > totalScore) {
            throw new IllegalArgumentException("Invalid Office score range");
        }
        items = List.copyOf(items);
        comparisonRows = List.copyOf(comparisonRows);
    }
}

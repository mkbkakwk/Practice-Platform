package com.oj.office.model;

public record OfficeComparisonDiff(
        String ruleId,
        String label,
        Object student,
        Object teacher,
        boolean match) {
}

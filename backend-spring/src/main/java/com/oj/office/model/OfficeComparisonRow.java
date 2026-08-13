package com.oj.office.model;

import java.util.List;

public record OfficeComparisonRow(
        int index,
        String studentText,
        String teacherText,
        List<OfficeComparisonDiff> diffs,
        boolean match) {

    public OfficeComparisonRow {
        diffs = List.copyOf(diffs);
    }
}

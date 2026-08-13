package com.oj.office.model;

import java.util.List;

public record OfficeTable(int index, List<List<String>> rows) {

    public OfficeTable {
        rows = rows.stream().map(List::copyOf).toList();
    }
}

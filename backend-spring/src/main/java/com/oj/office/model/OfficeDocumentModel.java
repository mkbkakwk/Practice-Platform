package com.oj.office.model;

import java.util.List;

public record OfficeDocumentModel(
        List<OfficeParagraph> paragraphs,
        List<OfficeTable> tables) {

    public OfficeDocumentModel {
        paragraphs = List.copyOf(paragraphs);
        tables = List.copyOf(tables);
    }
}

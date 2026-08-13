package com.oj.office.model;

public record OfficeRun(
        String text,
        String fontFamily,
        int fontSizeHundredths,
        boolean bold,
        boolean italic,
        boolean underline,
        String color) {
}

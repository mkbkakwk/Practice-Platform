package com.oj.office.model;

import java.util.List;

public record OfficeParagraph(
        int index,
        String text,
        String alignment,
        int firstLineIndentTwips,
        int leftIndentTwips,
        int rightIndentTwips,
        int spacingBeforeTwips,
        int spacingAfterTwips,
        int lineSpacingHundredths,
        List<OfficeRun> runs) {

    public OfficeParagraph {
        runs = List.copyOf(runs);
    }
}

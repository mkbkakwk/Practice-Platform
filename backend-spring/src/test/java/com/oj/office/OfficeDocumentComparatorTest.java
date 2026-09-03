package com.oj.office;

import com.oj.config.AppProperties;
import com.oj.office.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OfficeDocumentComparatorTest {

    private final OfficeDocumentComparator comparator = comparator(200);

    @Test
    void identicalDocumentReceivesFullScore() {
        OfficeJudgeResult result = comparator.compare(document("Hello", run("Hello"), "CENTER", "A"),
                document("Hello", run("Hello"), "CENTER", "A"));

        assertThat(result.judgeVersion()).isEqualTo("office-docx-v1");
        assertThat(result.totalScore()).isEqualTo(100);
        assertThat(result.earnedScore()).isEqualTo(100);
        assertThat(result.passed()).isTrue();
        assertThat(result.items()).isEmpty();
    }

    @Test
    void textFormattingAndStructureMismatchesDeductDeterministically() {
        OfficeDocumentModel expected = document("Hello", run("Hello"), "CENTER", "A");
        OfficeDocumentModel textWrong = document("World", run("World"), "CENTER", "A");
        OfficeDocumentModel formatWrong = document("Hello",
                new OfficeRun("Hello", "arial", 1000, false, true, true, "FF0000"), "LEFT", "A");
        OfficeDocumentModel structureWrong = new OfficeDocumentModel(List.of(), List.of());
        OfficeDocumentModel tableWrong = document("Hello", run("Hello"), "CENTER", "B");

        OfficeJudgeResult text = comparator.compare(expected, textWrong);
        OfficeJudgeResult format = comparator.compare(expected, formatWrong);
        OfficeJudgeResult structure = comparator.compare(expected, structureWrong);
        OfficeJudgeResult table = comparator.compare(expected, tableWrong);

        assertThat(text.earnedScore()).isLessThan(100);
        assertThat(text.items()).extracting(OfficeScoreItem::ruleId).anyMatch(id -> id.endsWith("-text"));
        assertThat(format.earnedScore()).isLessThan(100);
        assertThat(format.items()).extracting(OfficeScoreItem::ruleId)
                .anyMatch(id -> id.endsWith("-font"))
                .anyMatch(id -> id.endsWith("-size"))
                .anyMatch(id -> id.endsWith("-bold"))
                .anyMatch(id -> id.endsWith("-alignment"));
        assertThat(structure.items()).extracting(OfficeScoreItem::ruleId).contains("paragraph-0-exists");
        assertThat(table.items()).extracting(OfficeScoreItem::ruleId)
                .contains("table-0-row-0-cell-0-text");
    }

    @Test
    void repeatedJudgeTenTimesIsByteForByteDeterministicAtModelLevel() {
        OfficeDocumentModel expected = document("中文 emoji 😀", run("中文 emoji 😀"), "LEFT", "A");
        OfficeDocumentModel actual = document("中文 emoji 😃", run("中文 emoji 😃"), "LEFT", "B");
        OfficeJudgeResult first = comparator.compare(expected, actual);

        for (int run = 0; run < 10; run++) {
            assertThat(comparator.compare(expected, actual)).isEqualTo(first);
        }
    }

    @Test
    void resultItemsAreBoundedWhileTotalErrorsRemainObservable() {
        OfficeDocumentComparator bounded = comparator(2);
        OfficeJudgeResult result = bounded.compare(
                document("Expected", run("Expected"), "CENTER", "A"),
                document("Actual", new OfficeRun("Actual", "arial", 1000,
                        false, true, true, "FF0000"), "LEFT", "B"));

        assertThat(result.items()).hasSize(2);
        assertThat(result.totalErrorCount()).isGreaterThan(2);
        assertThat(result.truncated()).isTrue();
        assertThat(result.earnedScore()).isBetween(0, 100);
    }

    private OfficeDocumentComparator comparator(int maxItems) {
        AppProperties properties = new AppProperties();
        properties.getOffice().setMaxResultItems(maxItems);
        return new OfficeDocumentComparator(properties);
    }

    private OfficeDocumentModel document(String paragraphText, OfficeRun run, String alignment, String cell) {
        OfficeParagraph paragraph = new OfficeParagraph(0, paragraphText, alignment,
                0, 0, 0, 0, 0, 100, List.of(run));
        return new OfficeDocumentModel(List.of(paragraph),
                List.of(new OfficeTable(0, List.of(List.of(cell)))));
    }

    private OfficeRun run(String text) {
        return new OfficeRun(text, "calibri", 1200, true, false, false, "AUTO");
    }
}

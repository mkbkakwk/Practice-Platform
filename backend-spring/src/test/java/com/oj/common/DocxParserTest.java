package com.oj.common;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocxParserTest {

    @Test
    void parsesNormalAndEmptyDocumentsWithoutCrashing() throws Exception {
        List<Map<String, Object>> normal = parse("normal.docx");
        List<Map<String, Object>> empty = parse("empty.docx");

        assertThat(normal).hasSize(1);
        assertThat(normal.getFirst().get("text")).isEqualTo("Normal fixture paragraph");
        assertThat(empty).isEmpty();
    }

    @Test
    void damagedAndFakeDocumentsFailInAControlledWay() {
        assertThatThrownBy(() -> parse("damaged.docx")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> parse("fake.docx")).isInstanceOf(Exception.class);
    }

    @Test
    void equalFormattingMatchesAndClearlyDifferentFormattingDoesNot() throws Exception {
        List<Map<String, Object>> normal = parse("normal.docx");
        List<Map<String, Object>> same = parse("normal.docx");
        List<Map<String, Object>> different = parse("mixed-format.docx");

        List<Map<String, Object>> equalResult = DocComparator.compare(normal, same);
        List<Map<String, Object>> differentResult = DocComparator.compare(normal, different);

        assertThat(DocComparator.matchPercent(equalResult)).isEqualTo(100);
        assertThat(DocComparator.matchPercent(differentResult)).isZero();
        assertThat(differentResult.getFirst().get("match")).isEqualTo(false);
    }

    @Test
    void mixedFormattingDocumentsCurrentFirstNonEmptyRunBehavior() throws Exception {
        Map<String, Object> paragraph = parse("mixed-format.docx").getFirst();

        assertThat(paragraph.get("text")).isEqualTo("First run Second run");
        assertThat(paragraph.get("fontFamily")).isEqualTo("Calibri");
        assertThat(((Number) paragraph.get("fontSizePt")).doubleValue()).isEqualTo(12.0);
        assertThat(paragraph.get("bold")).isEqualTo(true);
        assertThat(paragraph.get("italic")).isEqualTo(false);
    }

    @Test
    void tableOnlyDocumentRecordsCurrentTableSupportBoundary() throws Exception {
        assertThat(parse("table.docx")).isEmpty();
    }

    private List<Map<String, Object>> parse(String name) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/docx/" + name)) {
            assertThat(input).as("fixture %s", name).isNotNull();
            return DocxParser.parse(input);
        }
    }
}

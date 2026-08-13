package com.oj.office;

import com.oj.config.AppProperties;
import com.oj.office.model.OfficeComparisonDiff;
import com.oj.office.model.OfficeComparisonRow;
import com.oj.office.model.OfficeDocumentModel;
import com.oj.office.model.OfficeJudgeResult;
import com.oj.office.model.OfficeParagraph;
import com.oj.office.model.OfficeRun;
import com.oj.office.model.OfficeScoreItem;
import com.oj.office.model.OfficeTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class OfficeDocumentComparator {

    public static final String JUDGE_VERSION = "office-docx-v1";

    private final int maxResultItems;

    public OfficeDocumentComparator(AppProperties properties) {
        this.maxResultItems = properties.getOffice().getMaxResultItems();
    }

    public OfficeJudgeResult compare(OfficeDocumentModel expected, OfficeDocumentModel actual) {
        Comparison comparison = new Comparison();
        compareParagraphs(expected.paragraphs(), actual.paragraphs(), comparison);
        compareTables(expected.tables(), actual.tables(), comparison);
        int earned = comparison.total == 0
                ? 100
                : (int) Math.round(comparison.passed * 100.0 / comparison.total);
        return new OfficeJudgeResult(
                JUDGE_VERSION,
                100,
                Math.max(0, Math.min(100, earned)),
                comparison.failed == 0,
                comparison.items,
                comparison.failed,
                comparison.failed > comparison.items.size(),
                comparison.rows);
    }

    private void compareParagraphs(
            List<OfficeParagraph> expected,
            List<OfficeParagraph> actual,
            Comparison comparison) {
        int max = Math.max(expected.size(), actual.size());
        for (int index = 0; index < max; index++) {
            OfficeParagraph e = index < expected.size() ? expected.get(index) : null;
            OfficeParagraph a = index < actual.size() ? actual.get(index) : null;
            List<OfficeComparisonDiff> diffs = new ArrayList<>();
            if (e == null || a == null) {
                comparison.rule(
                        "paragraph-" + index + "-exists",
                        "第%d段".formatted(index + 1),
                        e == null ? "不存在" : "存在",
                        a == null ? "不存在" : "存在",
                        e == null && a == null,
                        "段落结构不一致");
            } else {
                textDiff(comparison, diffs, "paragraph-" + index + "-text", "文字内容",
                        "第%d段".formatted(index + 1), e.text(), a.text());
                diff(comparison, diffs, "paragraph-" + index + "-alignment", "对齐方式",
                        "第%d段".formatted(index + 1), e.alignment(), a.alignment());
                diff(comparison, diffs, "paragraph-" + index + "-first-indent", "首行缩进(twips)",
                        "第%d段".formatted(index + 1), e.firstLineIndentTwips(), a.firstLineIndentTwips());
                diff(comparison, diffs, "paragraph-" + index + "-left-indent", "左缩进(twips)",
                        "第%d段".formatted(index + 1), e.leftIndentTwips(), a.leftIndentTwips());
                diff(comparison, diffs, "paragraph-" + index + "-right-indent", "右缩进(twips)",
                        "第%d段".formatted(index + 1), e.rightIndentTwips(), a.rightIndentTwips());
                diff(comparison, diffs, "paragraph-" + index + "-spacing-before", "段前距(twips)",
                        "第%d段".formatted(index + 1), e.spacingBeforeTwips(), a.spacingBeforeTwips());
                diff(comparison, diffs, "paragraph-" + index + "-spacing-after", "段后距(twips)",
                        "第%d段".formatted(index + 1), e.spacingAfterTwips(), a.spacingAfterTwips());
                diff(comparison, diffs, "paragraph-" + index + "-line-spacing", "行距(百分之一行)",
                        "第%d段".formatted(index + 1), e.lineSpacingHundredths(), a.lineSpacingHundredths());
                compareRuns(index, e.runs(), a.runs(), diffs, comparison);
            }
            if (comparison.rows.size() < maxResultItems) {
                comparison.rows.add(new OfficeComparisonRow(
                        index,
                        a == null ? "(无此段)" : safeText(a.text()),
                        e == null ? "(无此段)" : "(参考内容不公开)",
                        diffs,
                        e != null && a != null && diffs.stream().allMatch(OfficeComparisonDiff::match)));
            }
        }
    }

    private void compareRuns(
            int paragraphIndex,
            List<OfficeRun> expected,
            List<OfficeRun> actual,
            List<OfficeComparisonDiff> diffs,
            Comparison comparison) {
        int max = Math.max(expected.size(), actual.size());
        for (int index = 0; index < max; index++) {
            OfficeRun e = index < expected.size() ? expected.get(index) : null;
            OfficeRun a = index < actual.size() ? actual.get(index) : null;
            String target = "第%d段第%d文本片段".formatted(paragraphIndex + 1, index + 1);
            String prefix = "paragraph-%d-run-%d-".formatted(paragraphIndex, index);
            if (e == null || a == null) {
                diff(comparison, diffs, prefix + "exists", "文本片段结构", target,
                        e == null ? "不存在" : "存在", a == null ? "不存在" : "存在");
                continue;
            }
            textDiff(comparison, diffs, prefix + "text", "片段文字", target, e.text(), a.text());
            diff(comparison, diffs, prefix + "font", "字体", target, e.fontFamily(), a.fontFamily());
            diff(comparison, diffs, prefix + "size", "字号(百分之一pt)", target,
                    e.fontSizeHundredths(), a.fontSizeHundredths());
            diff(comparison, diffs, prefix + "bold", "加粗", target, e.bold(), a.bold());
            diff(comparison, diffs, prefix + "italic", "斜体", target, e.italic(), a.italic());
            diff(comparison, diffs, prefix + "underline", "下划线", target, e.underline(), a.underline());
            diff(comparison, diffs, prefix + "color", "颜色", target, e.color(), a.color());
        }
    }

    private void compareTables(
            List<OfficeTable> expected,
            List<OfficeTable> actual,
            Comparison comparison) {
        int max = Math.max(expected.size(), actual.size());
        for (int tableIndex = 0; tableIndex < max; tableIndex++) {
            OfficeTable e = tableIndex < expected.size() ? expected.get(tableIndex) : null;
            OfficeTable a = tableIndex < actual.size() ? actual.get(tableIndex) : null;
            String target = "第%d个表格".formatted(tableIndex + 1);
            if (e == null || a == null) {
                comparison.rule("table-" + tableIndex + "-exists", target,
                        e == null ? "不存在" : "存在", a == null ? "不存在" : "存在",
                        e == null && a == null, "表格结构不一致");
                continue;
            }
            comparison.rule("table-" + tableIndex + "-rows", target + "行数",
                    String.valueOf(e.rows().size()), String.valueOf(a.rows().size()),
                    e.rows().size() == a.rows().size(), "表格行数不一致");
            int rows = Math.max(e.rows().size(), a.rows().size());
            for (int row = 0; row < rows; row++) {
                List<String> er = row < e.rows().size() ? e.rows().get(row) : List.of();
                List<String> ar = row < a.rows().size() ? a.rows().get(row) : List.of();
                comparison.rule("table-%d-row-%d-columns".formatted(tableIndex, row),
                        target + "第" + (row + 1) + "行列数",
                        String.valueOf(er.size()), String.valueOf(ar.size()), er.size() == ar.size(),
                        "表格列数不一致");
                int cells = Math.max(er.size(), ar.size());
                for (int cell = 0; cell < cells; cell++) {
                    String ev = cell < er.size() ? er.get(cell) : "(无此单元格)";
                    String av = cell < ar.size() ? ar.get(cell) : "(无此单元格)";
                    boolean matches = Objects.equals(ev, av);
                    comparison.rule("table-%d-row-%d-cell-%d-text".formatted(tableIndex, row, cell),
                            target + "第" + (row + 1) + "行第" + (cell + 1) + "格",
                            "参考内容不公开", matches ? "匹配" : "不匹配", matches, "单元格文字不一致");
                }
            }
        }
    }

    private void diff(
            Comparison comparison,
            List<OfficeComparisonDiff> diffs,
            String ruleId,
            String label,
            String target,
            Object expected,
            Object actual) {
        boolean match = Objects.equals(expected, actual);
        comparison.rule(ruleId, target, value(expected), value(actual), match, label + "不一致");
        diffs.add(new OfficeComparisonDiff(ruleId, label, safeValue(actual), safeValue(expected), match));
    }

    private void textDiff(
            Comparison comparison,
            List<OfficeComparisonDiff> diffs,
            String ruleId,
            String label,
            String target,
            String expected,
            String actual) {
        boolean match = Objects.equals(expected, actual);
        comparison.rule(ruleId, target, "参考内容不公开", match ? "匹配" : "不匹配", match,
                label + "不一致");
        diffs.add(new OfficeComparisonDiff(ruleId, label, safeText(actual), "参考内容不公开", match));
    }

    private String value(Object value) {
        String text = value == null ? "未设置" : String.valueOf(value);
        return text.length() <= 512 ? text : text.substring(0, 509) + "...";
    }

    private Object safeValue(Object value) {
        if (value instanceof Number || value instanceof Boolean || value == null) return value;
        return value(value);
    }

    private String safeText(String value) {
        return value(value);
    }

    private final class Comparison {
        private int total;
        private int passed;
        private int failed;
        private final List<OfficeScoreItem> items = new ArrayList<>();
        private final List<OfficeComparisonRow> rows = new ArrayList<>();

        void rule(
                String ruleId,
                String target,
                String expected,
                String actual,
                boolean matches,
                String message) {
            total++;
            if (matches) {
                passed++;
                return;
            }
            failed++;
            if (items.size() < maxResultItems) {
                items.add(new OfficeScoreItem(
                        ruleId, target, expected, actual, 1, 0, false, message));
            }
        }
    }
}

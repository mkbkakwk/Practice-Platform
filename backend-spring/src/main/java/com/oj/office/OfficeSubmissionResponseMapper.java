package com.oj.office;

import com.oj.dto.OfficeSubmissionDtos;
import com.oj.entity.OfficeDocSubmissionEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OfficeSubmissionResponseMapper {

    private static final String REFERENCE_REDACTED = "参考内容不公开";
    private static final Pattern PARAGRAPH = Pattern.compile(
            "^paragraph-(\\d+)-(exists|text|alignment|first-indent|left-indent|right-indent|spacing-before|spacing-after|line-spacing)$");
    private static final Pattern RUN = Pattern.compile(
            "^paragraph-(\\d+)-run-(\\d+)-(exists|text|font|size|bold|italic|underline|color)$");
    private static final Pattern TABLE = Pattern.compile("^table-(\\d+)-(exists|rows)$");
    private static final Pattern TABLE_ROW = Pattern.compile("^table-(\\d+)-row-(\\d+)-columns$");
    private static final Pattern TABLE_CELL = Pattern.compile(
            "^table-(\\d+)-row-(\\d+)-cell-(\\d+)-text$");
    private static final Pattern JUDGE_VERSION = Pattern.compile("^[a-zA-Z0-9._-]{1,64}$");

    public OfficeSubmissionDtos.StudentSubmission student(OfficeDocSubmissionEntity submission) {
        return new OfficeSubmissionDtos.StudentSubmission(
                submission.getId(),
                submission.getUserId(),
                submission.getExerciseId(),
                submission.getContestProblemId(),
                submission.getStudentDocName(),
                submission.getStatus(),
                submission.getScore(),
                submission.getTeacherComment(),
                submission.getJudgeVersion(),
                studentResult(submission),
                submission.getErrorCategory(),
                submission.getJudgedAt(),
                submission.getCreatedAt());
    }

    public OfficeSubmissionDtos.ReviewerSubmission reviewer(OfficeDocSubmissionEntity submission) {
        return new OfficeSubmissionDtos.ReviewerSubmission(
                submission.getId(),
                submission.getUserId(),
                submission.getExerciseId(),
                submission.getContestProblemId(),
                submission.getStudentDocName(),
                submission.getAutoResult(),
                submission.getCompareResult(),
                submission.getStatus(),
                submission.getScore(),
                submission.getTeacherComment(),
                submission.getJudgeVersion(),
                submission.getResultDetail() == null ? Map.of() : Collections.unmodifiableMap(
                        new LinkedHashMap<>(submission.getResultDetail())),
                submission.getErrorCategory(),
                submission.getJudgedAt(),
                submission.getCreatedAt());
    }

    public OfficeSubmissionDtos.SubmissionSummary summary(OfficeDocSubmissionEntity submission) {
        return new OfficeSubmissionDtos.SubmissionSummary(
                submission.getId(),
                submission.getExerciseId(),
                submission.getContestProblemId(),
                submission.getUserId(),
                submission.getStudentDocName(),
                submission.getStatus(),
                submission.getScore(),
                submission.getCreatedAt());
    }

    private OfficeSubmissionDtos.StudentResultDetail studentResult(OfficeDocSubmissionEntity submission) {
        Map<String, Object> raw = submission.getResultDetail();
        if (raw == null) raw = Map.of();
        List<OfficeSubmissionDtos.StudentResultItem> items = new ArrayList<>();
        Object rawItems = raw.get("items");
        if (rawItems instanceof List<?> list) {
            for (Object value : list) {
                if (value instanceof Map<?, ?> item && items.size() < 200) {
                    items.add(studentItem(item));
                }
            }
        }
        String storedVersion = submission.getJudgeVersion();
        String judgeVersion = safeJudgeVersion(raw.get("judgeVersion"), storedVersion);
        return new OfficeSubmissionDtos.StudentResultDetail(
                judgeVersion,
                integer(raw.get("totalScore"), 100),
                integer(raw.get("earnedScore"), submission.getScore() == null ? 0 : submission.getScore()),
                bool(raw.get("passed"), "COMPLETED".equals(submission.getStatus())),
                items,
                integer(raw.get("totalErrorCount"), items.size()),
                bool(raw.get("truncated"), false));
    }

    private OfficeSubmissionDtos.StudentResultItem studentItem(Map<?, ?> raw) {
        Object rawRuleId = raw.get("ruleId");
        Rule rule = rule(rawRuleId == null ? "" : String.valueOf(rawRuleId));
        boolean passed = bool(raw.get("passed"), false);
        Object expected = rule.textContent ? REFERENCE_REDACTED : safeFormatValue(raw.get("expected"));
        Object actual = rule.textContent ? (passed ? "匹配" : "不匹配") : safeFormatValue(raw.get("actual"));
        return new OfficeSubmissionDtos.StudentResultItem(
                rule.id,
                rule.target,
                expected,
                actual,
                integer(raw.get("score"), 0),
                integer(raw.get("earned"), 0),
                passed,
                rule.label + (passed ? "符合要求" : "不符合要求"));
    }

    private Rule rule(String id) {
        Matcher paragraph = PARAGRAPH.matcher(id);
        if (paragraph.matches()) {
            String kind = paragraph.group(2);
            return known(id, "第" + oneBased(paragraph.group(1)) + "段", kind);
        }
        Matcher run = RUN.matcher(id);
        if (run.matches()) {
            String target = "第" + oneBased(run.group(1)) + "段第" + oneBased(run.group(2)) + "文本片段";
            return known(id, target, run.group(3));
        }
        Matcher table = TABLE.matcher(id);
        if (table.matches()) {
            return known(id, "第" + oneBased(table.group(1)) + "个表格", table.group(2));
        }
        Matcher row = TABLE_ROW.matcher(id);
        if (row.matches()) {
            return known(id, "第" + oneBased(row.group(1)) + "个表格第" + oneBased(row.group(2)) + "行", "columns");
        }
        Matcher cell = TABLE_CELL.matcher(id);
        if (cell.matches()) {
            String target = "第" + oneBased(cell.group(1)) + "个表格第" + oneBased(cell.group(2))
                    + "行第" + oneBased(cell.group(3)) + "格";
            return known(id, target, "text");
        }
        return new Rule("unsupported-rule", "文档规则", "文档规则", true);
    }

    private Rule known(String id, String target, String kind) {
        return new Rule(id, target, label(kind), "text".equals(kind));
    }

    private String label(String kind) {
        return switch (kind) {
            case "exists" -> "结构";
            case "text" -> "文字内容";
            case "alignment" -> "对齐方式";
            case "first-indent" -> "首行缩进";
            case "left-indent" -> "左缩进";
            case "right-indent" -> "右缩进";
            case "spacing-before" -> "段前距";
            case "spacing-after" -> "段后距";
            case "line-spacing" -> "行距";
            case "font" -> "字体";
            case "size" -> "字号";
            case "bold" -> "加粗";
            case "italic" -> "斜体";
            case "underline" -> "下划线";
            case "color" -> "颜色";
            case "rows" -> "表格行数";
            case "columns" -> "表格列数";
            default -> "文档规则";
        };
    }

    private int oneBased(String zeroBased) {
        try {
            return Math.addExact(Integer.parseInt(zeroBased), 1);
        } catch (ArithmeticException | NumberFormatException ignored) {
            return 1;
        }
    }

    private Object safeFormatValue(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean) return value;
        String text = String.valueOf(value);
        if (text.length() > 128 || text.chars().anyMatch(character -> Character.isISOControl(character))) {
            return "值不可显示";
        }
        return text;
    }

    private String safeJudgeVersion(Object value, String fallback) {
        String candidate = value == null ? fallback : String.valueOf(value);
        return candidate != null && JUDGE_VERSION.matcher(candidate).matches() ? candidate : "unknown";
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) return bool;
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private record Rule(String id, String target, String label, boolean textContent) {}
}

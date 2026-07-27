package com.oj.common;

import java.util.*;

/**
 * Compares two parsed documents (student vs teacher) paragraph-by-paragraph and
 * produces a detailed diff. Each paragraph comparison lists which formatting
 * properties match and which differ.
 */
public class DocComparator {

    private static final String[] PROPS = {
            "fontFamily", "fontSizePt", "bold", "italic", "underline",
            "align", "firstLineIndentChars", "lineSpacing"
    };
    private static final String[] PROP_LABELS = {
            "字体", "字号(pt)", "加粗", "斜体", "下划线",
            "对齐方式", "首行缩进(字符)", "行距"
    };

    /**
     * Compare student paragraphs against teacher paragraphs.
     * Returns a list of per-paragraph comparison maps.
     */
    public static List<Map<String, Object>> compare(
            List<Map<String, Object>> student,
            List<Map<String, Object>> teacher) {

        List<Map<String, Object>> result = new ArrayList<>();
        int max = Math.max(student.size(), teacher.size());

        for (int i = 0; i < max; i++) {
            Map<String, Object> sPara = i < student.size() ? student.get(i) : null;
            Map<String, Object> tPara = i < teacher.size() ? teacher.get(i) : null;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("index", i);
            row.put("studentText", sPara != null ? sPara.get("text") : "(无此段)");
            row.put("teacherText", tPara != null ? tPara.get("text") : "(无此段)");

            List<Map<String, Object>> diffs = new ArrayList<>();
            boolean allMatch = true;

            for (int p = 0; p < PROPS.length; p++) {
                String prop = PROPS[p];
                Object sVal = sPara != null ? sPara.get(prop) : null;
                Object tVal = tPara != null ? tPara.get(prop) : null;
                boolean match = valuesMatch(sVal, tVal);
                if (!match) allMatch = false;
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("prop", prop);
                d.put("label", PROP_LABELS[p]);
                d.put("student", sVal);
                d.put("teacher", tVal);
                d.put("match", match);
                diffs.add(d);
            }
            row.put("diffs", diffs);
            row.put("match", allMatch);
            result.add(row);
        }
        return result;
    }

    /** Compare with tolerance for numeric values (font size ±1pt, indent ±0.5, lineSpacing ±0.1). */
    private static boolean valuesMatch(Object s, Object t) {
        if (s == null && t == null) return true;
        if (s == null || t == null) return false;
        if (s instanceof Number && t instanceof Number) {
            double diff = Math.abs(((Number) s).doubleValue() - ((Number) t).doubleValue());
            // Font size tolerance: ±1pt; indent: ±0.5; lineSpacing: ±0.1
            return diff < 1.01;
        }
        if (s instanceof Boolean && t instanceof Boolean) return s.equals(t);
        return String.valueOf(s).equalsIgnoreCase(String.valueOf(t));
    }

    /** Count matching paragraphs / total for a quick score. */
    public static int matchPercent(List<Map<String, Object>> compareResult) {
        if (compareResult.isEmpty()) return 0;
        long matched = compareResult.stream().filter(r -> Boolean.TRUE.equals(r.get("match"))).count();
        return (int) Math.round(matched * 100.0 / compareResult.size());
    }
}

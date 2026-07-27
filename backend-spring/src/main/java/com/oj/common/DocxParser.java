package com.oj.common;

import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a .docx file with Apache POI and extracts per-paragraph formatting
 * (font, size, bold/italic/underline, alignment, indent, line spacing).
 * Each paragraph becomes a Map that can be serialized to JSON for comparison.
 */
public class DocxParser {

    /**
     * Parse a .docx file and return a list of paragraph-format maps.
     * Each map has keys: index, text, fontFamily, fontSizePt, bold, italic,
     * underline, align, firstLineIndentChars, lineSpacing.
     */
    public static List<Map<String, Object>> parse(String filePath) throws Exception {
        try (InputStream in = new FileInputStream(filePath)) {
            return parse(in);
        }
    }

    public static List<Map<String, Object>> parse(InputStream in) throws Exception {
        List<Map<String, Object>> result = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(in)) {
            int idx = 0;
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                if (text == null) text = "";
                text = text.trim();
                // Skip empty paragraphs but still count them for index alignment.
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("index", idx++);
                p.put("text", text);

                // Paragraph-level: alignment
                ParagraphAlignment align = para.getAlignment();
                p.put("align", align == null ? "LEFT" : align.name());

                // First-line indent (POI returns twips = 1/20 pt; 1 char ≈ 12pt at 小四)
                int indentTwips = para.getIndentationFirstLine();
                // Convert twips to approximate char count (1 char ≈ 240 twips for 小四/12pt)
                double indentChars = indentTwips <= 0 ? 0.0 : Math.round(indentTwips / 240.0 * 10) / 10.0;
                p.put("firstLineIndentChars", indentChars);

                // Line spacing
                p.put("lineSpacing", extractLineSpacing(para));

                // Run-level: take the first non-empty run as representative
                XWPFRun repRun = null;
                for (XWPFRun r : para.getRuns()) {
                    String t = r == null ? null : r.text();
                    if (t != null && !t.isBlank()) { repRun = r; break; }
                }
                if (repRun != null) {
                    p.put("fontFamily", repRun.getFontFamily() != null ? repRun.getFontFamily() : "");
                    double sz = repRun.getFontSize();
                    p.put("fontSizePt", sz > 0 ? sz : 0);
                    p.put("bold", Boolean.TRUE.equals(repRun.isBold()));
                    p.put("italic", Boolean.TRUE.equals(repRun.isItalic()));
                    UnderlinePatterns ul = repRun.getUnderline();
                    p.put("underline", ul != null && ul != UnderlinePatterns.NONE);
                    String color = repRun.getColor();
                    p.put("color", color != null ? color : "");
                } else {
                    p.put("fontFamily", "");
                    p.put("fontSizePt", 0);
                    p.put("bold", false);
                    p.put("italic", false);
                    p.put("underline", false);
                    p.put("color", "");
                }
                result.add(p);
            }
        }
        return result;
    }

    /** Extract line spacing value: 1.0 / 1.5 / 2.0 etc. Returns 0 if unset. */
    private static double extractLineSpacing(XWPFParagraph para) {
        try {
            CTPPr ppr = para.getCTP().getPPr();
            if (ppr == null) return 0;
            CTSpacing spacing = ppr.getSpacing();
            if (spacing == null) return 0;
            // line attribute: in 240ths of a line (240 = single, 360 = 1.5, 480 = double)
            // getLine() returns Object (schema-generated BigInteger); cast to Number.
            if (spacing.isSetLine()) {
                Object lineObj = spacing.getLine();
                double lineVal = lineObj instanceof Number ? ((Number) lineObj).doubleValue() : 0;
                return Math.round(lineVal / 240.0 * 100) / 100.0;
            }
        } catch (Exception ignored) {}
        return 0;
    }
}

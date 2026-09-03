package com.oj.office;

import com.oj.config.AppProperties;
import com.oj.office.model.OfficeDocumentModel;
import com.oj.office.model.OfficeParagraph;
import com.oj.office.model.OfficeRun;
import com.oj.office.model.OfficeTable;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.oj.office.OfficeDocumentException.Category.INVALID_DOCUMENT;
import static com.oj.office.OfficeDocumentException.Category.PARSING_FAILED;

@Component
public class OfficeDocumentParser {

    private final AppProperties.Office limits;

    public OfficeDocumentParser(AppProperties properties) {
        this.limits = properties.getOffice();
        ZipSecureFile.setMinInflateRatio(limits.getMinInflateRatio());
        ZipSecureFile.setMaxEntrySize(limits.getMaxEntryBytes());
        ZipSecureFile.setMaxTextSize(limits.getMaxTextChars());
    }

    public OfficeDocumentModel parse(Path path) {
        try (OPCPackage pkg = OPCPackage.open(path.toFile(), PackageAccess.READ);
             XWPFDocument document = new XWPFDocument(pkg)) {
            Counter counter = new Counter();
            List<OfficeParagraph> paragraphs = new ArrayList<>();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                paragraphs.add(paragraph(paragraph, paragraphs.size(), counter));
            }
            List<OfficeTable> tables = new ArrayList<>();
            for (XWPFTable table : document.getTables()) {
                tables.add(table(table, tables.size(), counter));
            }
            return new OfficeDocumentModel(paragraphs, tables);
        } catch (OfficeDocumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new OfficeDocumentException(INVALID_DOCUMENT, "Apache POI rejected document", exception);
        }
    }

    private OfficeParagraph paragraph(XWPFParagraph paragraph, int index, Counter counter) {
        counter.element();
        List<OfficeRun> runs = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (XWPFRun run : paragraph.getRuns()) {
            counter.element();
            String runText = normalizeText(run.text(), false);
            counter.text(runText.length());
            text.append(runText);
            runs.add(new OfficeRun(
                    runText,
                    normalizeFont(run.getFontFamily()),
                    sizeHundredths(run.getFontSizeAsDouble()),
                    run.isBold(),
                    run.isItalic(),
                    run.getUnderline() != null && run.getUnderline() != UnderlinePatterns.NONE,
                    normalizeColor(run.getColor())));
        }
        String paragraphText = normalizeText(text.toString(), true);
        counter.text(Math.max(0, paragraphText.length() - text.length()));
        ParagraphAlignment alignment = paragraph.getAlignment();
        return new OfficeParagraph(
                index,
                paragraphText,
                alignment == null || alignment == ParagraphAlignment.LEFT ? "LEFT" : alignment.name(),
                canonicalTwips(paragraph.getIndentationFirstLine()),
                canonicalTwips(paragraph.getIndentationLeft()),
                canonicalTwips(paragraph.getIndentationRight()),
                canonicalTwips(paragraph.getSpacingBefore()),
                canonicalTwips(paragraph.getSpacingAfter()),
                lineSpacingHundredths(paragraph),
                runs);
    }

    private OfficeTable table(XWPFTable table, int index, Counter counter) {
        counter.element();
        List<List<String>> rows = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            counter.element();
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                counter.element();
                String text = normalizeText(cell.getText(), true);
                counter.text(text.length());
                cells.add(text);
            }
            rows.add(cells);
        }
        return new OfficeTable(index, rows);
    }

    private String normalizeText(String value, boolean stripTrailing) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(
                value.replace("\r\n", "\n").replace('\r', '\n'), Normalizer.Form.NFC);
        return stripTrailing ? normalized.stripTrailing() : normalized;
    }

    private String normalizeFont(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private String normalizeColor(String value) {
        if (value == null || value.isBlank() || "auto".equalsIgnoreCase(value)) return "AUTO";
        return value.toUpperCase(Locale.ROOT);
    }

    private int sizeHundredths(Double value) {
        return value == null || value <= 0 ? 0 : (int) Math.round(value * 100);
    }

    private int canonicalTwips(int value) {
        return value == -1 ? 0 : value;
    }

    private int lineSpacingHundredths(XWPFParagraph paragraph) {
        try {
            CTPPr properties = paragraph.getCTP().getPPr();
            if (properties == null) return 0;
            CTSpacing spacing = properties.getSpacing();
            if (spacing == null || !spacing.isSetLine()) return 0;
            Object line = spacing.getLine();
            double value = line instanceof Number number ? number.doubleValue() : 0;
            return (int) Math.round(value / 240.0 * 100);
        } catch (RuntimeException exception) {
            throw new OfficeDocumentException(PARSING_FAILED, "invalid paragraph spacing", exception);
        }
    }

    private final class Counter {
        private int elements;
        private int chars;

        void element() {
            if (++elements > limits.getMaxDocumentElements()) {
                throw new OfficeDocumentException(INVALID_DOCUMENT, "document element limit exceeded");
            }
        }

        void text(int count) {
            chars += count;
            if (chars > limits.getMaxTextChars()) {
                throw new OfficeDocumentException(INVALID_DOCUMENT, "document text limit exceeded");
            }
        }
    }
}

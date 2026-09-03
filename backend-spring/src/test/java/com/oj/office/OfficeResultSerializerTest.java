package com.oj.office;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.config.AppProperties;
import com.oj.office.model.OfficeComparisonDiff;
import com.oj.office.model.OfficeComparisonRow;
import com.oj.office.model.OfficeJudgeResult;
import com.oj.office.model.OfficeScoreItem;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class OfficeResultSerializerTest {

    @Test
    void structuredAndCompatibilityResultsRespectTheUtf8ByteLimit() throws Exception {
        AppProperties properties = new AppProperties();
        properties.getOffice().setMaxResultBytes(1024);
        ObjectMapper mapper = new ObjectMapper();
        OfficeResultSerializer serializer = new OfficeResultSerializer(mapper, properties);
        String text = "测".repeat(500);
        List<OfficeScoreItem> items = IntStream.range(0, 20)
                .mapToObj(index -> new OfficeScoreItem(
                        "rule-" + index, "target", text, text, 1, 0, false, text))
                .toList();
        List<OfficeComparisonRow> rows = IntStream.range(0, 20)
                .mapToObj(index -> new OfficeComparisonRow(index, text, "参考内容不公开",
                        List.of(new OfficeComparisonDiff("rule-" + index, "文字", text,
                                "参考内容不公开", false)), false))
                .toList();
        OfficeJudgeResult result = new OfficeJudgeResult(
                OfficeDocumentComparator.JUDGE_VERSION, 100, 0, false,
                items, items.size(), true, rows);

        String detail = serializer.json(serializer.structured(result));
        String compatibilityRows = serializer.comparisonRows(result);

        assertThat(detail.getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(1024);
        assertThat(mapper.readTree(detail).path("truncated").asBoolean()).isTrue();
        assertThat(compatibilityRows.getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(1024);
        assertThat(mapper.readTree(compatibilityRows).isArray()).isTrue();
    }
}

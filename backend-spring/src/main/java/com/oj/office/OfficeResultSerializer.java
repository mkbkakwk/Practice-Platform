package com.oj.office;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.config.AppProperties;
import com.oj.office.model.OfficeComparisonRow;
import com.oj.office.model.OfficeJudgeResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
public class OfficeResultSerializer {

    private final ObjectMapper objectMapper;
    private final int maxBytes;

    public OfficeResultSerializer(ObjectMapper objectMapper, AppProperties properties) {
        this.objectMapper = objectMapper;
        this.maxBytes = Math.min(256 * 1024,
                Math.max(1024, properties.getOffice().getMaxResultBytes()));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> structured(OfficeJudgeResult result) {
        Map<String, Object> detail = objectMapper.convertValue(result, Map.class);
        if (fits(json(detail))) return detail;
        Map<String, Object> summary = Map.of(
                "judgeVersion", result.judgeVersion(),
                "totalScore", result.totalScore(),
                "earnedScore", result.earnedScore(),
                "passed", result.passed(),
                "items", List.of(),
                "totalErrorCount", result.totalErrorCount(),
                "truncated", true);
        if (!fits(json(summary))) {
            throw new IllegalStateException("Office result byte limit is too small for its safe summary");
        }
        return summary;
    }

    public String comparisonRows(OfficeJudgeResult result) {
        List<OfficeComparisonRow> rows = result.comparisonRows();
        int size = rows.size();
        while (size > 0) {
            String json = json(rows.subList(0, size));
            if (fits(json)) return json;
            size /= 2;
        }
        return "[]";
    }

    public String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize bounded Office judge result", exception);
        }
    }

    private boolean fits(String json) {
        return json.getBytes(StandardCharsets.UTF_8).length <= maxBytes;
    }
}

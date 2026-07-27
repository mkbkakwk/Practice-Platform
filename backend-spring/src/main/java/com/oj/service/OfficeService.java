package com.oj.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import com.oj.dto.*;
import com.oj.entity.OfficeQuestionEntity;
import com.oj.entity.OfficeRecordEntity;
import com.oj.mapper.OfficeQuestionMapper;
import com.oj.mapper.OfficeRecordMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class OfficeService {

    private final OfficeQuestionMapper questionMapper;
    private final OfficeRecordMapper recordMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Set<String> APP_TYPES = Set.of("WORD", "EXCEL", "PPT");
    private static final Set<String> DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");
    private static final Set<String> QUESTION_TYPES = Set.of("SINGLE_CHOICE", "MULTI_CHOICE", "TRUE_FALSE");

    public OfficeService(OfficeQuestionMapper questionMapper, OfficeRecordMapper recordMapper) {
        this.questionMapper = questionMapper;
        this.recordMapper = recordMapper;
    }

    public List<OfficeQuestionListItem> list(int page, int pageSize, String appType, String difficulty) {
        QueryWrapper<OfficeQuestionEntity> qw = new QueryWrapper<>();
        if (!CurrentUser.isAdmin()) {
            qw.eq("visible", true);
        }
        if (appType != null && APP_TYPES.contains(appType.toUpperCase())) {
            qw.eq("app_type", appType.toUpperCase());
        }
        if (difficulty != null && DIFFICULTIES.contains(difficulty.toUpperCase())) {
            qw.eq("difficulty", difficulty.toUpperCase());
        }
        qw.orderByAsc("id");
        Page<OfficeQuestionEntity> p = questionMapper.selectPage(new Page<>(page, pageSize), qw);
        return p.getRecords().stream().map(e -> new OfficeQuestionListItem(
                e.getId(), e.getAppType(), e.getCategory(), e.getDifficulty(),
                e.getQuestionType(), e.getContent(), e.getVisible()
        )).toList();
    }

    public long count(String appType, String difficulty) {
        QueryWrapper<OfficeQuestionEntity> qw = new QueryWrapper<>();
        if (!CurrentUser.isAdmin()) {
            qw.eq("visible", true);
        }
        if (appType != null && APP_TYPES.contains(appType.toUpperCase())) {
            qw.eq("app_type", appType.toUpperCase());
        }
        if (difficulty != null && DIFFICULTIES.contains(difficulty.toUpperCase())) {
            qw.eq("difficulty", difficulty.toUpperCase());
        }
        return questionMapper.selectCount(qw);
    }

    /** Detail for practice: includes options but NOT answer/explanation (unless admin). */
    public OfficeQuestionDetail getById(int id) {
        OfficeQuestionEntity e = questionMapper.selectById(id);
        if (e == null || (!Boolean.TRUE.equals(e.getVisible()) && !CurrentUser.isAdmin())) {
            throw ApiException.notFound("题目不存在");
        }
        OfficeQuestionDetail d = new OfficeQuestionDetail();
        d.setId(e.getId());
        d.setAppType(e.getAppType());
        d.setCategory(e.getCategory());
        d.setDifficulty(e.getDifficulty());
        d.setQuestionType(e.getQuestionType());
        d.setContent(e.getContent());
        d.setOptions(parseStringList(e.getOptions()));
        d.setVisible(e.getVisible());
        // Admin gets answer + explanation for editing.
        if (CurrentUser.isAdmin()) {
            d.setAnswer(e.getAnswer());
            d.setExplanation(e.getExplanation());
        }
        return d;
    }

    public OfficeQuestionEntity create(OfficeQuestionUpsertRequest req) {
        validate(req);
        OfficeQuestionEntity e = new OfficeQuestionEntity();
        applyToEntity(e, req);
        questionMapper.insert(e);
        return e;
    }

    public OfficeQuestionEntity update(int id, OfficeQuestionUpsertRequest req) {
        validate(req);
        OfficeQuestionEntity e = questionMapper.selectById(id);
        if (e == null) throw ApiException.notFound("题目不存在");
        applyToEntity(e, req);
        questionMapper.updateById(e);
        return e;
    }

    public OfficeSubmitResult submit(OfficeSubmitRequest req) {
        Integer userId = CurrentUser.getId();
        if (userId == null) throw ApiException.unauthorized("请先登录");

        OfficeQuestionEntity e = questionMapper.selectById(req.getQuestionId());
        if (e == null || (!Boolean.TRUE.equals(e.getVisible()) && !CurrentUser.isAdmin())) {
            throw ApiException.notFound("题目不存在");
        }
        // Normalize selected: sort and join with comma for multi-choice comparison.
        List<String> selected = req.getSelected() == null ? List.of() : req.getSelected();
        String selectedNorm = normalize(selected);
        String answerNorm = e.getAnswer() == null ? "" : e.getAnswer().trim();
        boolean correct = selectedNorm.equals(answerNorm);

        // Persist the attempt record.
        OfficeRecordEntity rec = new OfficeRecordEntity();
        rec.setUserId(userId);
        rec.setQuestionId(e.getId());
        rec.setSelected(serialize(selected));
        rec.setCorrect(correct);
        recordMapper.insert(rec);

        return new OfficeSubmitResult(correct, e.getAnswer(), e.getExplanation());
    }

    public OfficeStats stats() {
        Integer userId = CurrentUser.getId();
        if (userId == null) throw ApiException.unauthorized("请先登录");
        QueryWrapper<OfficeRecordEntity> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);
        List<OfficeRecordEntity> records = recordMapper.selectList(qw);

        OfficeStats s = new OfficeStats();
        s.setTotalAnswered(records.size());
        long correct = records.stream().filter(r -> Boolean.TRUE.equals(r.getCorrect())).count();
        s.setCorrectCount(correct);
        s.setAccuracy(records.isEmpty() ? 0.0 : (correct * 100.0 / records.size()));

        // Breakdown by app type: join with question to know app type.
        // To avoid N+1, batch-fetch the questions referenced.
        Set<Integer> qIds = records.stream().map(OfficeRecordEntity::getQuestionId).collect(Collectors.toSet());
        Map<Integer, String> qAppType = new HashMap<>();
        if (!qIds.isEmpty()) {
            List<OfficeQuestionEntity> qs = questionMapper.selectBatchIds(qIds);
            for (OfficeQuestionEntity q : qs) qAppType.put(q.getId(), q.getAppType());
        }
        for (OfficeRecordEntity r : records) {
            String app = qAppType.getOrDefault(r.getQuestionId(), "");
            boolean ok = Boolean.TRUE.equals(r.getCorrect());
            switch (app) {
                case "WORD" -> { s.setWordAnswered(s.getWordAnswered() + 1); if (ok) s.setWordCorrect(s.getWordCorrect() + 1); }
                case "EXCEL" -> { s.setExcelAnswered(s.getExcelAnswered() + 1); if (ok) s.setExcelCorrect(s.getExcelCorrect() + 1); }
                case "PPT" -> { s.setPptAnswered(s.getPptAnswered() + 1); if (ok) s.setPptCorrect(s.getPptCorrect() + 1); }
            }
        }
        return s;
    }

    // ---- helpers ----

    private void validate(OfficeQuestionUpsertRequest req) {
        if (!APP_TYPES.contains(req.getAppType().toUpperCase())) {
            throw ApiException.badRequest("appType 必须是 WORD/EXCEL/PPT");
        }
        if (!QUESTION_TYPES.contains(req.getQuestionType().toUpperCase())) {
            throw ApiException.badRequest("questionType 必须是 SINGLE_CHOICE/MULTI_CHOICE/TRUE_FALSE");
        }
    }

    private void applyToEntity(OfficeQuestionEntity e, OfficeQuestionUpsertRequest req) {
        e.setAppType(req.getAppType().toUpperCase());
        e.setCategory(req.getCategory());
        e.setDifficulty(req.getDifficulty() == null || req.getDifficulty().isBlank()
                ? "EASY" : req.getDifficulty().toUpperCase());
        e.setQuestionType(req.getQuestionType().toUpperCase());
        e.setContent(req.getContent());
        e.setOptions(serialize(req.getOptions()));
        e.setAnswer(req.getAnswer() == null ? "" : req.getAnswer().trim());
        e.setExplanation(req.getExplanation() == null ? "" : req.getExplanation());
        e.setVisible(req.getVisible() == null || req.getVisible());
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String serialize(List<String> list) {
        if (list == null) return "[]";
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception ex) {
            return "[]";
        }
    }

    /** Normalize a selection list into a canonical string for comparison:
     *  sorted, comma-joined. e.g. ["2","0"] -> "0,2"; ["T"] -> "T". */
    private String normalize(List<String> selected) {
        List<String> cleaned = selected.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .sorted()
                .toList();
        return String.join(",", cleaned);
    }
}

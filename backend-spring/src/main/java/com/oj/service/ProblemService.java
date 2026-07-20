package com.oj.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.common.ApiException;
import com.oj.dto.ProblemDetail;
import com.oj.dto.ProblemListItem;
import com.oj.dto.ProblemUpsertRequest;
import com.oj.entity.ProblemEntity;
import com.oj.mapper.ProblemMapper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class ProblemService {

    private final ProblemMapper problemMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProblemService(ProblemMapper problemMapper) {
        this.problemMapper = problemMapper;
    }

    /** Parse a JSON array stored as text in the DB into a Java List. */
    private Object parseJsonArray(String json) {
        if (json == null || json.isBlank()) return java.util.List.of();
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    public List<ProblemListItem> list(int page, int pageSize, String difficulty) {
        QueryWrapper<ProblemEntity> qw = new QueryWrapper<>();
        // Admin sees all problems (including hidden); regular users see only visible ones.
        if (!com.oj.common.CurrentUser.isAdmin()) {
            qw.eq("visible", true);
        }
        if (difficulty != null && !difficulty.isBlank()
                && Arrays.asList("EASY", "MEDIUM", "HARD").contains(difficulty)) {
            qw.eq("difficulty", difficulty);
        }
        qw.orderByAsc("id");
        Page<ProblemEntity> p = problemMapper.selectPage(new Page<>(page, pageSize), qw);
        return p.getRecords().stream().map(e -> new ProblemListItem(
                e.getId(), e.getSlug(), e.getTitle(), e.getDifficulty(),
                e.getTags() != null ? e.getTags() : new String[0],
                e.getTimeLimit(), e.getMemoryLimit(), e.getVisible()
        )).toList();
    }

    public long count(String difficulty) {
        QueryWrapper<ProblemEntity> qw = new QueryWrapper<>();
        if (!com.oj.common.CurrentUser.isAdmin()) {
            qw.eq("visible", true);
        }
        if (difficulty != null && !difficulty.isBlank()
                && Arrays.asList("EASY", "MEDIUM", "HARD").contains(difficulty)) {
            qw.eq("difficulty", difficulty);
        }
        return problemMapper.selectCount(qw);
    }

    public ProblemDetail getBySlug(String slug) {
        ProblemEntity e = problemMapper.selectOne(new QueryWrapper<ProblemEntity>().eq("slug", slug));
        // Admin can access hidden problems; regular users cannot.
        if (e == null || (!Boolean.TRUE.equals(e.getVisible()) && !com.oj.common.CurrentUser.isAdmin())) {
            throw ApiException.notFound("题目不存在");
        }
        ProblemDetail d = new ProblemDetail();
        d.setId(e.getId());
        d.setSlug(e.getSlug());
        d.setTitle(e.getTitle());
        d.setDescription(e.getDescription());
        d.setInputFmt(e.getInputFmt());
        d.setOutputFmt(e.getOutputFmt());
        d.setDifficulty(e.getDifficulty());
        d.setTags(e.getTags() != null ? e.getTags() : new String[0]);
        d.setTimeLimit(e.getTimeLimit());
        d.setMemoryLimit(e.getMemoryLimit());
        d.setSamples(parseJsonArray(e.getSamples()));
        // Only admin gets the hidden test cases (for editing).
        if (com.oj.common.CurrentUser.isAdmin()) {
            d.setTestCases(parseJsonArray(e.getTestCases()));
        }
        return d;
    }

    public ProblemEntity getEntityById(int id) {
        ProblemEntity e = problemMapper.selectById(id);
        if (e == null) throw ApiException.notFound("题目不存在");
        return e;
    }

    public ProblemEntity create(ProblemUpsertRequest req) {
        ProblemEntity exists = problemMapper.selectOne(new QueryWrapper<ProblemEntity>().eq("slug", req.getSlug()));
        if (exists != null) throw ApiException.conflict("slug 已存在");
        ProblemEntity e = new ProblemEntity();
        applyToEntity(e, req);
        problemMapper.insert(e);
        return e;
    }

    public ProblemEntity update(String slug, ProblemUpsertRequest req) {
        ProblemEntity e = problemMapper.selectOne(new QueryWrapper<ProblemEntity>().eq("slug", slug));
        if (e == null) throw ApiException.notFound("题目不存在");
        applyToEntity(e, req);
        problemMapper.updateById(e);
        return e;
    }

    private void applyToEntity(ProblemEntity e, ProblemUpsertRequest req) {
        e.setSlug(req.getSlug());
        e.setTitle(req.getTitle());
        e.setDescription(req.getDescription());
        e.setInputFmt(req.getInputFmt() == null ? "" : req.getInputFmt());
        e.setOutputFmt(req.getOutputFmt() == null ? "" : req.getOutputFmt());
        e.setDifficulty(req.getDifficulty());
        e.setTimeLimit(req.getTimeLimit());
        e.setMemoryLimit(req.getMemoryLimit());
        e.setTags(req.getTags() == null ? new String[0] : req.getTags());
        e.setSamples(serialize(req.getSamples()));
        e.setTestCases(serialize(req.getTestCases()));
        e.setVisible(req.getVisible() == null || req.getVisible());
    }

    /** Serialize a JSON array/object received as Object into a JSON string for DB storage. */
    private String serialize(Object obj) {
        if (obj == null) return "[]";
        if (obj instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}

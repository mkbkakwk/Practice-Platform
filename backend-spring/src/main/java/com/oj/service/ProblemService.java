package com.oj.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import com.oj.dto.ProblemDetail;
import com.oj.dto.ProblemListItem;
import com.oj.dto.ProblemUpsertRequest;
import com.oj.entity.ProblemEntity;
import com.oj.entity.SubmissionEntity;
import com.oj.mapper.ProblemMapper;
import com.oj.mapper.SubmissionMapper;
import com.oj.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProblemService {

    private static final Set<String> DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");

    private final ProblemMapper problemMapper;
    private final SubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProblemService(ProblemMapper problemMapper, SubmissionMapper submissionMapper, UserMapper userMapper) {
        this.problemMapper = problemMapper;
        this.submissionMapper = submissionMapper;
        this.userMapper = userMapper;
    }

    public List<ProblemListItem> list(int page, int pageSize, String difficulty) {
        QueryWrapper<ProblemEntity> query = baseListQuery(difficulty);
        query.eq("visible", true).orderByAsc("id");
        return toListItems(problemMapper.selectPage(new Page<>(page, pageSize), query).getRecords());
    }

    public long count(String difficulty) {
        QueryWrapper<ProblemEntity> query = baseListQuery(difficulty);
        query.eq("visible", true);
        return problemMapper.selectCount(query);
    }

    public List<ProblemListItem> listManage(int page, int pageSize, String difficulty) {
        QueryWrapper<ProblemEntity> query = managementQuery(difficulty);
        query.orderByDesc("id");
        return toListItems(problemMapper.selectPage(new Page<>(page, pageSize), query).getRecords());
    }

    public long countManage(String difficulty) {
        return problemMapper.selectCount(managementQuery(difficulty));
    }

    public ProblemDetail getBySlug(String slug) {
        ProblemEntity entity = findBySlug(slug);
        if (!Boolean.TRUE.equals(entity.getVisible()) && !CurrentUser.canManage(entity.getCreatedBy())) {
            throw ApiException.notFound("题目不存在");
        }
        return toDetail(entity);
    }

    /** Internal lookup used by submission views and submit validation. */
    public ProblemEntity getEntityById(int id) {
        ProblemEntity entity = problemMapper.selectById(id);
        if (entity == null) throw ApiException.notFound("题目不存在");
        return entity;
    }

    public ProblemEntity create(ProblemUpsertRequest request) {
        CurrentUser.requireContentManager();
        Integer userId = CurrentUser.getId();
        if (userId == null) throw ApiException.unauthorized("请先登录");
        if (problemMapper.selectCount(new QueryWrapper<ProblemEntity>().eq("slug", request.getSlug())) > 0) {
            throw ApiException.conflict("slug 已存在");
        }

        ProblemEntity entity = new ProblemEntity();
        applyToEntity(entity, request);
        entity.setCreatedBy(userId);
        problemMapper.insert(entity);
        entity.setCreatorUsername(CurrentUser.getUsername());
        return entity;
    }

    public ProblemEntity update(String slug, ProblemUpsertRequest request) {
        ProblemEntity entity = findBySlug(slug);
        CurrentUser.requireCanManage(entity.getCreatedBy());
        if (!slug.equals(request.getSlug())
                && problemMapper.selectCount(new QueryWrapper<ProblemEntity>().eq("slug", request.getSlug())) > 0) {
            throw ApiException.conflict("slug 已存在");
        }
        applyToEntity(entity, request);
        problemMapper.updateById(entity);
        entity.setCreatorUsername(loadCreatorUsername(entity.getCreatedBy()));
        return entity;
    }

    public ProblemEntity setVisible(String slug, boolean visible) {
        ProblemEntity entity = findBySlug(slug);
        CurrentUser.requireCanManage(entity.getCreatedBy());
        entity.setVisible(visible);
        problemMapper.updateById(entity);
        entity.setCreatorUsername(loadCreatorUsername(entity.getCreatedBy()));
        return entity;
    }

    @Transactional
    public Map<String, Object> hardDelete(String slug) {
        ProblemEntity entity = findBySlug(slug);
        CurrentUser.requireCanManage(entity.getCreatedBy());

        List<SubmissionEntity> submissions = submissionMapper.selectList(
                new QueryWrapper<SubmissionEntity>().eq("problem_id", entity.getId()));
        if (!CurrentUser.isAdmin() && !submissions.isEmpty()) {
            throw ApiException.conflict("该内容已有学生提交，只能停用，不能彻底删除。");
        }

        Set<Integer> affectedUserIds = submissions.stream()
                .map(SubmissionEntity::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        int deletedSubmissions = submissionMapper.delete(
                new QueryWrapper<SubmissionEntity>().eq("problem_id", entity.getId()));
        problemMapper.deleteById(entity.getId());

        for (Integer userId : affectedUserIds) {
            userMapper.recalculateSolved(userId);
        }
        return Map.of(
                "deleted", true,
                "deletedSubmissions", deletedSubmissions,
                "affectedUsers", affectedUserIds.size()
        );
    }

    private QueryWrapper<ProblemEntity> baseListQuery(String difficulty) {
        QueryWrapper<ProblemEntity> query = new QueryWrapper<>();
        if (difficulty != null && DIFFICULTIES.contains(difficulty.toUpperCase())) {
            query.eq("difficulty", difficulty.toUpperCase());
        }
        return query;
    }

    private QueryWrapper<ProblemEntity> managementQuery(String difficulty) {
        CurrentUser.requireContentManager();
        QueryWrapper<ProblemEntity> query = baseListQuery(difficulty);
        if (!CurrentUser.isAdmin()) {
            query.eq("created_by", CurrentUser.getId());
        }
        return query;
    }

    private ProblemEntity findBySlug(String slug) {
        ProblemEntity entity = problemMapper.selectOne(new QueryWrapper<ProblemEntity>().eq("slug", slug));
        if (entity == null) throw ApiException.notFound("题目不存在");
        return entity;
    }

    private ProblemDetail toDetail(ProblemEntity entity) {
        ProblemDetail detail = new ProblemDetail();
        detail.setId(entity.getId());
        detail.setSlug(entity.getSlug());
        detail.setTitle(entity.getTitle());
        detail.setDescription(entity.getDescription());
        detail.setInputFmt(entity.getInputFmt());
        detail.setOutputFmt(entity.getOutputFmt());
        detail.setDifficulty(entity.getDifficulty());
        detail.setTags(entity.getTags() == null ? new String[0] : entity.getTags());
        detail.setTimeLimit(entity.getTimeLimit());
        detail.setMemoryLimit(entity.getMemoryLimit());
        detail.setSamples(parseJsonArray(entity.getSamples()));
        detail.setVisible(entity.getVisible());
        detail.setCreatedBy(entity.getCreatedBy());
        detail.setCreatorUsername(loadCreatorUsername(entity.getCreatedBy()));
        detail.setCreatedAt(entity.getCreatedAt());
        if (CurrentUser.canManage(entity.getCreatedBy())) {
            detail.setTestCases(parseJsonArray(entity.getTestCases()));
        }
        return detail;
    }

    private List<ProblemListItem> toListItems(List<ProblemEntity> records) {
        Map<Integer, String> creatorNames = loadCreatorNames(records);
        Map<Integer, Long> submissionCounts = loadSubmissionCounts(records);
        return records.stream().map(entity -> new ProblemListItem(
                entity.getId(), entity.getSlug(), entity.getTitle(), entity.getDifficulty(),
                entity.getTags() == null ? new String[0] : entity.getTags(),
                entity.getTimeLimit(), entity.getMemoryLimit(), entity.getVisible(),
                entity.getCreatedBy(), entity.getCreatedBy() == null ? null : creatorNames.get(entity.getCreatedBy()),
                submissionCounts.getOrDefault(entity.getId(), 0L), entity.getCreatedAt()
        )).toList();
    }

    private Map<Integer, String> loadCreatorNames(List<ProblemEntity> problems) {
        Set<Integer> creatorIds = problems.stream()
                .map(ProblemEntity::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (creatorIds.isEmpty()) return Map.of();
        Map<Integer, String> names = new HashMap<>();
        userMapper.selectBatchIds(creatorIds).forEach(user -> names.put(user.getId(), user.getUsername()));
        return names;
    }

    private Map<Integer, Long> loadSubmissionCounts(List<ProblemEntity> problems) {
        Set<Integer> ids = problems.stream().map(ProblemEntity::getId).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        List<SubmissionEntity> submissions = submissionMapper.selectList(
                new QueryWrapper<SubmissionEntity>().in("problem_id", ids).select("problem_id"));
        return submissions.stream().collect(Collectors.groupingBy(SubmissionEntity::getProblemId, Collectors.counting()));
    }

    private String loadCreatorUsername(Integer createdBy) {
        if (createdBy == null) return null;
        var user = userMapper.selectById(createdBy);
        return user == null ? null : user.getUsername();
    }

    private void applyToEntity(ProblemEntity entity, ProblemUpsertRequest request) {
        entity.setSlug(request.getSlug());
        entity.setTitle(request.getTitle());
        entity.setDescription(request.getDescription());
        entity.setInputFmt(request.getInputFmt() == null ? "" : request.getInputFmt());
        entity.setOutputFmt(request.getOutputFmt() == null ? "" : request.getOutputFmt());
        entity.setDifficulty(request.getDifficulty());
        entity.setTimeLimit(request.getTimeLimit());
        entity.setMemoryLimit(request.getMemoryLimit());
        entity.setTags(request.getTags() == null ? new String[0] : request.getTags());
        entity.setSamples(serialize(request.getSamples()));
        entity.setTestCases(serializeTestCases(request.getTestCases()));
        entity.setVisible(request.getVisible() == null || request.getVisible());
    }

    private Object parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String serializeTestCases(Object value) {
        if (value == null) {
            throw ApiException.badRequest("测试点不能为空，至少需要 1 个测试点");
        }

        JsonNode node;
        try {
            node = objectMapper.valueToTree(value);
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("测试点格式无效");
        }
        if (!node.isArray()) {
            throw ApiException.badRequest("测试点必须是 JSON 数组");
        }
        if (node.isEmpty()) {
            throw ApiException.badRequest("测试点不能为空，至少需要 1 个测试点");
        }
        for (int index = 0; index < node.size(); index++) {
            JsonNode testCase = node.get(index);
            JsonNode input = testCase == null ? null : testCase.get("input");
            JsonNode output = testCase == null ? null : testCase.get("output");
            if (testCase == null || !testCase.isObject() || testCase.size() != 2
                    || !isTextOrNull(input) || !isTextOrNull(output)) {
                throw ApiException.badRequest(
                        "第 " + (index + 1) + " 个测试点必须只包含字符串 input 和 output");
            }
        }
        return node.toString();
    }

    private boolean isTextOrNull(JsonNode value) {
        return value != null && (value.isTextual() || value.isNull());
    }

    private String serialize(Object value) {
        if (value == null) return "[]";
        if (value instanceof String stringValue) return stringValue;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "[]";
        }
    }
}

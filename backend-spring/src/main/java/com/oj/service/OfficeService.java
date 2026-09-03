package com.oj.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import com.oj.contest.ContentVisibility;
import com.oj.dto.*;
import com.oj.entity.OfficeQuestionEntity;
import com.oj.entity.OfficeRecordEntity;
import com.oj.entity.ContestProblemEntity;
import com.oj.mapper.ContestProblemMapper;
import com.oj.mapper.OfficeQuestionMapper;
import com.oj.mapper.OfficeRecordMapper;
import com.oj.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class OfficeService {

    private static final Set<String> APP_TYPES = Set.of("WORD", "EXCEL", "PPT");
    private static final Set<String> DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");
    private static final Set<String> QUESTION_TYPES = Set.of("SINGLE_CHOICE", "MULTI_CHOICE", "TRUE_FALSE");

    private final OfficeQuestionMapper questionMapper;
    private final OfficeRecordMapper recordMapper;
    private final UserMapper userMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OfficeService(OfficeQuestionMapper questionMapper, OfficeRecordMapper recordMapper,
                         UserMapper userMapper, ContestProblemMapper contestProblemMapper) {
        this.questionMapper = questionMapper;
        this.recordMapper = recordMapper;
        this.userMapper = userMapper;
        this.contestProblemMapper = contestProblemMapper;
    }

    public List<OfficeQuestionListItem> list(int page, int pageSize, String appType, String difficulty) {
        QueryWrapper<OfficeQuestionEntity> query = baseListQuery(appType, difficulty);
        query.eq("visible", true).eq("content_visibility", ContentVisibility.PUBLIC.name()).orderByAsc("id");
        return toListItems(questionMapper.selectPage(new Page<>(page, pageSize), query).getRecords());
    }

    public long count(String appType, String difficulty) {
        QueryWrapper<OfficeQuestionEntity> query = baseListQuery(appType, difficulty);
        query.eq("visible", true).eq("content_visibility", ContentVisibility.PUBLIC.name());
        return questionMapper.selectCount(query);
    }

    public List<OfficeQuestionListItem> listManage(int page, int pageSize, String appType, String difficulty) {
        QueryWrapper<OfficeQuestionEntity> query = managementQuery(appType, difficulty);
        query.orderByDesc("id");
        return toListItems(questionMapper.selectPage(new Page<>(page, pageSize), query).getRecords());
    }

    public long countManage(String appType, String difficulty) {
        return questionMapper.selectCount(managementQuery(appType, difficulty));
    }

    public OfficeQuestionDetail getById(int id) {
        OfficeQuestionEntity entity = findById(id);
        if ((!Boolean.TRUE.equals(entity.getVisible())
                || ContentVisibility.CONTEST_ONLY.name().equals(entity.getContentVisibility()))
                && !CurrentUser.canManage(entity.getCreatedBy())) {
            throw ApiException.notFound("题目不存在");
        }
        OfficeQuestionDetail detail = detail(entity);
        if (CurrentUser.canManage(entity.getCreatedBy())) {
            detail.setAnswer(entity.getAnswer());
            detail.setExplanation(entity.getExplanation());
        }
        return detail;
    }

    public OfficeQuestionDetail getContestQuestion(int id) {
        OfficeQuestionEntity entity = findById(id);
        if (!Boolean.TRUE.equals(entity.getVisible())) {
            throw ApiException.notFound("题目不存在");
        }
        return detail(entity);
    }

    public OfficeQuestionEntity requireContestReady(int id) {
        OfficeQuestionEntity entity = questionMapper.selectById(id);
        if (entity == null || !Boolean.TRUE.equals(entity.getVisible())) {
            throw ApiException.notFound("Office 选择题不存在或已停用");
        }
        validateContestReady(entity);
        return entity;
    }

    private OfficeQuestionDetail detail(OfficeQuestionEntity entity) {
        OfficeQuestionDetail detail = new OfficeQuestionDetail();
        detail.setId(entity.getId());
        detail.setAppType(entity.getAppType());
        detail.setCategory(entity.getCategory());
        detail.setDifficulty(entity.getDifficulty());
        detail.setQuestionType(entity.getQuestionType());
        detail.setContent(entity.getContent());
        detail.setOptions(parseStringList(entity.getOptions()));
        detail.setVisible(entity.getVisible());
        detail.setContentVisibility(entity.getContentVisibility());
        detail.setCreatedBy(entity.getCreatedBy());
        detail.setCreatorUsername(loadCreatorUsername(entity.getCreatedBy()));
        detail.setCreatedAt(entity.getCreatedAt());
        return detail;
    }

    public OfficeQuestionEntity create(OfficeQuestionUpsertRequest request) {
        CurrentUser.requireContentManager();
        Integer userId = CurrentUser.getId();
        if (userId == null) throw ApiException.unauthorized("请先登录");
        validate(request);
        OfficeQuestionEntity entity = new OfficeQuestionEntity();
        applyToEntity(entity, request);
        entity.setCreatedBy(userId);
        questionMapper.insert(entity);
        entity.setCreatorUsername(CurrentUser.getUsername());
        return entity;
    }

    public OfficeQuestionEntity update(int id, OfficeQuestionUpsertRequest request) {
        OfficeQuestionEntity entity = findById(id);
        CurrentUser.requireCanManage(entity.getCreatedBy());
        validate(request);
        applyToEntity(entity, request);
        questionMapper.updateById(entity);
        entity.setCreatorUsername(loadCreatorUsername(entity.getCreatedBy()));
        return entity;
    }

    public OfficeQuestionEntity setVisible(int id, boolean visible) {
        OfficeQuestionEntity entity = findById(id);
        CurrentUser.requireCanManage(entity.getCreatedBy());
        entity.setVisible(visible);
        questionMapper.updateById(entity);
        entity.setCreatorUsername(loadCreatorUsername(entity.getCreatedBy()));
        return entity;
    }

    @Transactional
    public Map<String, Object> hardDelete(int id) {
        OfficeQuestionEntity entity = findById(id);
        CurrentUser.requireCanManage(entity.getCreatedBy());
        if (contestProblemMapper.selectCount(new QueryWrapper<ContestProblemEntity>()
                .eq("office_question_id", id)) > 0) {
            throw ApiException.conflict("该题目已加入比赛，只能停用，不能彻底删除。");
        }
        List<OfficeRecordEntity> records = recordMapper.selectList(
                new QueryWrapper<OfficeRecordEntity>().eq("question_id", id));
        if (!CurrentUser.isAdmin() && !records.isEmpty()) {
            throw ApiException.conflict("该内容已有学生提交，只能停用，不能彻底删除。");
        }

        Set<Integer> affectedUserIds = records.stream()
                .map(OfficeRecordEntity::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        int deletedRecords = recordMapper.delete(new QueryWrapper<OfficeRecordEntity>().eq("question_id", id));
        questionMapper.deleteById(id);
        return Map.of(
                "deleted", true,
                "deletedRecords", deletedRecords,
                "affectedUsers", affectedUserIds.size()
        );
    }

    public OfficeSubmitResult submit(OfficeSubmitRequest request) {
        Integer userId = CurrentUser.getId();
        if (userId == null) throw ApiException.unauthorized("请先登录");

        OfficeQuestionEntity entity = questionMapper.selectById(request.getQuestionId());
        if (entity == null) throw ApiException.notFound("题目不存在");
        if (!Boolean.TRUE.equals(entity.getVisible())) {
            throw ApiException.conflict("该题目已停用，无法继续作答");
        }
        if (!ContentVisibility.PUBLIC.name().equals(entity.getContentVisibility())) {
            throw ApiException.notFound("题目不存在");
        }

        List<String> selected = request.getSelected() == null ? List.of() : request.getSelected();
        String selectedNorm = normalize(selected);
        String answerNorm = entity.getAnswer() == null ? "" : entity.getAnswer().trim();
        boolean correct = selectedNorm.equals(answerNorm);

        OfficeRecordEntity record = new OfficeRecordEntity();
        record.setUserId(userId);
        record.setQuestionId(entity.getId());
        record.setSelected(serialize(selected));
        record.setCorrect(correct);
        recordMapper.insert(record);
        return new OfficeSubmitResult(correct, entity.getAnswer(), entity.getExplanation());
    }

    public ContestDtos.ChoiceSubmission submitContest(int questionId, List<String> selected,
                                                       long contestProblemId) {
        Integer userId = CurrentUser.getId();
        if (userId == null) throw ApiException.unauthorized("请先登录");
        OfficeQuestionEntity entity = questionMapper.selectById(questionId);
        if (entity == null || !Boolean.TRUE.equals(entity.getVisible())) {
            throw ApiException.notFound("题目不存在");
        }
        List<String> safeSelected = validateContestSelection(entity, selected);
        boolean correct = normalize(safeSelected).equals(entity.getAnswer() == null ? "" : entity.getAnswer().trim());
        OfficeRecordEntity record = new OfficeRecordEntity();
        record.setUserId(userId);
        record.setQuestionId(questionId);
        record.setContestProblemId(contestProblemId);
        record.setSelected(serialize(safeSelected));
        record.setCorrect(correct);
        recordMapper.insert(record);
        record = recordMapper.selectById(record.getId());
        return new ContestDtos.ChoiceSubmission(record.getId(), contestProblemId,
                safeSelected, correct, record.getCreatedAt());
    }

    public OfficeStats stats() {
        Integer userId = CurrentUser.getId();
        if (userId == null) throw ApiException.unauthorized("请先登录");
        List<OfficeRecordEntity> records = recordMapper.selectList(
                new QueryWrapper<OfficeRecordEntity>().eq("user_id", userId).isNull("contest_problem_id"));

        OfficeStats stats = new OfficeStats();
        stats.setTotalAnswered(records.size());
        long correct = records.stream().filter(record -> Boolean.TRUE.equals(record.getCorrect())).count();
        stats.setCorrectCount(correct);
        stats.setAccuracy(records.isEmpty() ? 0.0 : correct * 100.0 / records.size());

        Set<Integer> questionIds = records.stream().map(OfficeRecordEntity::getQuestionId).collect(Collectors.toSet());
        Map<Integer, String> appTypes = new HashMap<>();
        if (!questionIds.isEmpty()) {
            questionMapper.selectBatchIds(questionIds)
                    .forEach(question -> appTypes.put(question.getId(), question.getAppType()));
        }
        for (OfficeRecordEntity record : records) {
            String appType = appTypes.getOrDefault(record.getQuestionId(), "");
            boolean isCorrect = Boolean.TRUE.equals(record.getCorrect());
            switch (appType) {
                case "WORD" -> {
                    stats.setWordAnswered(stats.getWordAnswered() + 1);
                    if (isCorrect) stats.setWordCorrect(stats.getWordCorrect() + 1);
                }
                case "EXCEL" -> {
                    stats.setExcelAnswered(stats.getExcelAnswered() + 1);
                    if (isCorrect) stats.setExcelCorrect(stats.getExcelCorrect() + 1);
                }
                case "PPT" -> {
                    stats.setPptAnswered(stats.getPptAnswered() + 1);
                    if (isCorrect) stats.setPptCorrect(stats.getPptCorrect() + 1);
                }
            }
        }
        return stats;
    }

    private QueryWrapper<OfficeQuestionEntity> baseListQuery(String appType, String difficulty) {
        QueryWrapper<OfficeQuestionEntity> query = new QueryWrapper<>();
        if (appType != null && APP_TYPES.contains(appType.toUpperCase())) {
            query.eq("app_type", appType.toUpperCase());
        }
        if (difficulty != null && DIFFICULTIES.contains(difficulty.toUpperCase())) {
            query.eq("difficulty", difficulty.toUpperCase());
        }
        return query;
    }

    private QueryWrapper<OfficeQuestionEntity> managementQuery(String appType, String difficulty) {
        CurrentUser.requireContentManager();
        QueryWrapper<OfficeQuestionEntity> query = baseListQuery(appType, difficulty);
        if (!CurrentUser.isAdmin()) query.eq("created_by", CurrentUser.getId());
        return query;
    }

    private OfficeQuestionEntity findById(int id) {
        OfficeQuestionEntity entity = questionMapper.selectById(id);
        if (entity == null) throw ApiException.notFound("题目不存在");
        return entity;
    }

    private List<OfficeQuestionListItem> toListItems(List<OfficeQuestionEntity> questions) {
        Map<Integer, String> creatorNames = loadCreatorNames(questions);
        Map<Integer, Long> submissionCounts = loadSubmissionCounts(questions);
        return questions.stream().map(entity -> new OfficeQuestionListItem(
                entity.getId(), entity.getAppType(), entity.getCategory(), entity.getDifficulty(),
                entity.getQuestionType(), entity.getContent(), entity.getVisible(), entity.getContentVisibility(),
                entity.getCreatedBy(),
                entity.getCreatedBy() == null ? null : creatorNames.get(entity.getCreatedBy()),
                submissionCounts.getOrDefault(entity.getId(), 0L), entity.getCreatedAt()
        )).toList();
    }

    private Map<Integer, String> loadCreatorNames(List<OfficeQuestionEntity> questions) {
        Set<Integer> creatorIds = questions.stream()
                .map(OfficeQuestionEntity::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (creatorIds.isEmpty()) return Map.of();
        Map<Integer, String> names = new HashMap<>();
        userMapper.selectBatchIds(creatorIds).forEach(user -> names.put(user.getId(), user.getUsername()));
        return names;
    }

    private Map<Integer, Long> loadSubmissionCounts(List<OfficeQuestionEntity> questions) {
        Set<Integer> ids = questions.stream().map(OfficeQuestionEntity::getId).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        List<OfficeRecordEntity> records = recordMapper.selectList(
                new QueryWrapper<OfficeRecordEntity>().in("question_id", ids).select("question_id"));
        return records.stream().collect(Collectors.groupingBy(OfficeRecordEntity::getQuestionId, Collectors.counting()));
    }

    private String loadCreatorUsername(Integer createdBy) {
        if (createdBy == null) return null;
        var user = userMapper.selectById(createdBy);
        return user == null ? null : user.getUsername();
    }

    private void validate(OfficeQuestionUpsertRequest request) {
        if (!APP_TYPES.contains(request.getAppType().toUpperCase())) {
            throw ApiException.badRequest("appType 必须是 WORD/EXCEL/PPT");
        }
        if (!QUESTION_TYPES.contains(request.getQuestionType().toUpperCase())) {
            throw ApiException.badRequest("questionType 必须是 SINGLE_CHOICE/MULTI_CHOICE/TRUE_FALSE");
        }
    }

    private void validateContestReady(OfficeQuestionEntity entity) {
        if (entity.getContent() == null || entity.getContent().isBlank()
                || entity.getAnswer() == null || entity.getAnswer().isBlank()) {
            throw ApiException.conflict("Office 选择题缺少题干或正确答案，不能加入比赛");
        }
        List<String> options = parseStringList(entity.getOptions());
        if (options.size() < 2 || options.stream().anyMatch(option -> option == null || option.isBlank())) {
            throw ApiException.conflict("Office 选择题选项配置不完整，不能加入比赛");
        }
        String type = entity.getQuestionType();
        if (!QUESTION_TYPES.contains(type)) {
            throw ApiException.conflict("Office 选择题类型无效，不能加入比赛");
        }
        if ("TRUE_FALSE".equals(type)) {
            if (!Set.of("T", "F").contains(entity.getAnswer().trim())) {
                throw ApiException.conflict("判断题正确答案配置无效，不能加入比赛");
            }
            return;
        }
        List<String> answers = Arrays.stream(entity.getAnswer().split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).toList();
        if (answers.isEmpty() || ("SINGLE_CHOICE".equals(type) && answers.size() != 1)
                || answers.stream().distinct().count() != answers.size()
                || answers.stream().anyMatch(value -> !isOptionIndex(value, options.size()))) {
            throw ApiException.conflict("Office 选择题正确答案配置无效，不能加入比赛");
        }
    }

    private List<String> validateContestSelection(OfficeQuestionEntity entity, List<String> selected) {
        validateContestReady(entity);
        List<String> safe = selected == null ? List.of() : selected.stream()
                .filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty()).toList();
        List<String> options = parseStringList(entity.getOptions());
        boolean invalid = safe.isEmpty() || safe.stream().distinct().count() != safe.size();
        if ("TRUE_FALSE".equals(entity.getQuestionType())) {
            invalid = invalid || safe.size() != 1 || !Set.of("T", "F").contains(safe.get(0));
        } else {
            invalid = invalid || ("SINGLE_CHOICE".equals(entity.getQuestionType()) && safe.size() != 1)
                    || safe.stream().anyMatch(value -> !isOptionIndex(value, options.size()));
        }
        if (invalid) throw ApiException.badRequest("所选答案格式无效");
        return List.copyOf(safe);
    }

    private boolean isOptionIndex(String value, int optionCount) {
        try {
            int index = Integer.parseInt(value);
            return index >= 0 && index < optionCount;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void applyToEntity(OfficeQuestionEntity entity, OfficeQuestionUpsertRequest request) {
        entity.setAppType(request.getAppType().toUpperCase());
        entity.setCategory(request.getCategory());
        entity.setDifficulty(request.getDifficulty() == null || request.getDifficulty().isBlank()
                ? "EASY" : request.getDifficulty().toUpperCase());
        entity.setQuestionType(request.getQuestionType().toUpperCase());
        entity.setContent(request.getContent());
        entity.setOptions(serialize(request.getOptions()));
        entity.setAnswer(request.getAnswer() == null ? "" : request.getAnswer().trim());
        entity.setExplanation(request.getExplanation() == null ? "" : request.getExplanation());
        entity.setVisible(request.getVisible() == null || request.getVisible());
        entity.setContentVisibility(ContentVisibility.parse(request.getContentVisibility()).name());
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String serialize(List<String> list) {
        if (list == null) return "[]";
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private String normalize(List<String> selected) {
        return selected.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .sorted()
                .collect(Collectors.joining(","));
    }
}

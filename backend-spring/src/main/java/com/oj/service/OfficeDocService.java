package com.oj.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import com.oj.contest.ContestContentAccessPolicy;
import com.oj.contest.ContestProblemType;
import com.oj.contest.ContentVisibility;
import com.oj.mapper.ContestProblemMapper;
import com.oj.dto.OfficeExerciseCreateRequest;
import com.oj.dto.OfficeSubmissionDtos;
import com.oj.dto.ReviewRequest;
import com.oj.entity.OfficeDocSubmissionEntity;
import com.oj.entity.OfficeExerciseEntity;
import com.oj.mapper.OfficeDocSubmissionMapper;
import com.oj.mapper.OfficeExerciseMapper;
import com.oj.mapper.UserMapper;
import com.oj.office.OfficeDocumentComparator;
import com.oj.office.OfficeDocumentException;
import com.oj.office.OfficeDocumentParser;
import com.oj.office.OfficeFileValidator;
import com.oj.office.OfficeJudgeConcurrencyGate;
import com.oj.office.OfficeResultSerializer;
import com.oj.office.OfficeSubmissionResponseMapper;
import com.oj.office.OfficeStorageService;
import com.oj.office.model.OfficeDocumentModel;
import com.oj.office.model.OfficeJudgeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OfficeDocService {

    private static final Logger log = LoggerFactory.getLogger(OfficeDocService.class);

    private final OfficeExerciseMapper exerciseMapper;
    private final OfficeDocSubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final OfficeFileValidator validator;
    private final OfficeStorageService storage;
    private final OfficeDocumentParser parser;
    private final OfficeDocumentComparator comparator;
    private final OfficeJudgeConcurrencyGate concurrencyGate;
    private final OfficeResultSerializer resultSerializer;
    private final OfficeSubmissionResponseMapper responseMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final ContestContentAccessPolicy contestAccess;

    public OfficeDocService(OfficeExerciseMapper exerciseMapper,
                            OfficeDocSubmissionMapper submissionMapper,
                            UserMapper userMapper,
                            OfficeFileValidator validator,
                            OfficeStorageService storage,
                            OfficeDocumentParser parser,
                            OfficeDocumentComparator comparator,
                            OfficeJudgeConcurrencyGate concurrencyGate,
                            OfficeResultSerializer resultSerializer,
                            OfficeSubmissionResponseMapper responseMapper,
                            ContestProblemMapper contestProblemMapper,
                            ContestContentAccessPolicy contestAccess) {
        this.exerciseMapper = exerciseMapper;
        this.submissionMapper = submissionMapper;
        this.userMapper = userMapper;
        this.validator = validator;
        this.storage = storage;
        this.parser = parser;
        this.comparator = comparator;
        this.concurrencyGate = concurrencyGate;
        this.resultSerializer = resultSerializer;
        this.responseMapper = responseMapper;
        this.contestProblemMapper = contestProblemMapper;
        this.contestAccess = contestAccess;
    }

    public Map<String, Object> listExercises(int page, int pageSize) {
        QueryWrapper<OfficeExerciseEntity> query = new QueryWrapper<>();
        query.eq("visible", true).eq("content_visibility", ContentVisibility.PUBLIC.name()).orderByDesc("id");
        return listResponse(page, pageSize, query);
    }

    public Map<String, Object> listExercisesManage(int page, int pageSize) {
        CurrentUser.requireContentManager();
        QueryWrapper<OfficeExerciseEntity> query = new QueryWrapper<>();
        if (!CurrentUser.isAdmin()) query.eq("created_by", CurrentUser.getId());
        query.orderByDesc("id");
        return listResponse(page, pageSize, query);
    }

    public OfficeExerciseEntity getExercise(int id) {
        OfficeExerciseEntity exercise = findExercise(id);
        boolean manager = CurrentUser.canManage(exercise.getCreatedBy());
        boolean contestAllowed = ContentVisibility.CONTEST_ONLY.name().equals(exercise.getContentVisibility())
                && contestAccess.canReadContestOnly(ContestProblemType.OFFICE_DOCX, exercise.getId());
        if ((!Boolean.TRUE.equals(exercise.getVisible())
                || ContentVisibility.CONTEST_ONLY.name().equals(exercise.getContentVisibility()))
                && !manager && !contestAllowed) {
            throw ApiException.notFound("练习不存在");
        }
        exercise.setCreatorUsername(loadCreatorUsername(exercise.getCreatedBy()));
        return exercise;
    }

    public OfficeExerciseEntity createExercise(OfficeExerciseCreateRequest request) {
        CurrentUser.requireContentManager();
        Integer userId = CurrentUser.getId();
        if (userId == null) throw ApiException.unauthorized("请先登录");
        OfficeExerciseEntity exercise = new OfficeExerciseEntity();
        applyToEntity(exercise, request);
        exercise.setCreatedBy(userId);
        exerciseMapper.insert(exercise);
        exercise.setCreatorUsername(CurrentUser.getUsername());
        return exercise;
    }

    public OfficeExerciseEntity updateExercise(int id, OfficeExerciseCreateRequest request) {
        OfficeExerciseEntity exercise = findExercise(id);
        CurrentUser.requireCanManage(exercise.getCreatedBy());
        if (contestProblemMapper.selectCount(new QueryWrapper<com.oj.entity.ContestProblemEntity>()
                .eq("office_exercise_id", exercise.getId())) > 0) {
            throw ApiException.conflict("该练习已被比赛引用，不能彻底删除");
        }
        applyToEntity(exercise, request);
        exerciseMapper.updateById(exercise);
        exercise.setCreatorUsername(loadCreatorUsername(exercise.getCreatedBy()));
        return exercise;
    }

    public OfficeExerciseEntity setVisible(int id, boolean visible) {
        OfficeExerciseEntity exercise = findExercise(id);
        CurrentUser.requireCanManage(exercise.getCreatedBy());
        exercise.setVisible(visible);
        exerciseMapper.updateById(exercise);
        exercise.setCreatorUsername(loadCreatorUsername(exercise.getCreatedBy()));
        return exercise;
    }

    @Transactional
    public Map<String, Object> hardDelete(int id) {
        OfficeExerciseEntity exercise = findExercise(id);
        CurrentUser.requireCanManage(exercise.getCreatedBy());
        List<OfficeDocSubmissionEntity> submissions = submissionMapper.selectList(
                new QueryWrapper<OfficeDocSubmissionEntity>().eq("exercise_id", id));
        if (!CurrentUser.isAdmin() && !submissions.isEmpty()) {
            throw ApiException.conflict("该内容已有学生提交，只能停用，不能彻底删除。");
        }

        Set<Integer> affectedUserIds = submissions.stream()
                .map(OfficeDocSubmissionEntity::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> candidatePaths = submissions.stream()
                .map(OfficeDocSubmissionEntity::getStudentDocPath)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (exercise.getTeacherDocPath() != null) candidatePaths.add(exercise.getTeacherDocPath());
        if (exercise.getStarterDocPath() != null) candidatePaths.add(exercise.getStarterDocPath());

        int deletedSubmissions = submissionMapper.delete(
                new QueryWrapper<OfficeDocSubmissionEntity>().eq("exercise_id", id));
        exerciseMapper.deleteById(id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", true);
        result.put("deletedSubmissions", deletedSubmissions);
        result.put("deletedFiles", 0);
        result.put("affectedUsers", affectedUserIds.size());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                int deletedFiles = 0;
                for (String path : candidatePaths) {
                    if (deleteFileIfUnused(path)) deletedFiles++;
                }
                result.put("deletedFiles", deletedFiles);
            }
        });
        return result;
    }

    public Map<String, Object> uploadTeacherDoc(int exerciseId, MultipartFile file) {
        OfficeExerciseEntity exercise = findExercise(exerciseId);
        CurrentUser.requireCanManage(exercise.getCreatedBy());
        String displayName = validator.validateMetadata(file);
        String oldPath = exercise.getTeacherDocPath();
        OfficeStorageService.StagedDocument staged = storage.stage(file, displayName);
        OfficeStorageService.StoredDocument stored = null;
        try {
            validator.validateContainer(staged.path());
            parser.parse(staged.path());
            stored = storage.commit(staged);
            exercise.setTeacherDocPath(storage.path(stored));
            exercise.setTeacherDocName(displayName);
            exerciseMapper.updateById(exercise);
        } catch (OfficeDocumentException exception) {
            storage.discard(staged);
            if (stored != null) storage.delete(storage.path(stored));
            throw documentApiException(exception);
        } catch (RuntimeException exception) {
            storage.discard(staged);
            if (stored != null) storage.delete(storage.path(stored));
            throw exception;
        }
        if (oldPath != null && !oldPath.equals(storage.path(stored))) deleteFileIfUnused(oldPath);
        return Map.of("teacherDocName", displayName);
    }

    public Map<String, Object> uploadStarterDoc(int exerciseId, MultipartFile file) {
        OfficeExerciseEntity exercise = findExercise(exerciseId);
        CurrentUser.requireCanManage(exercise.getCreatedBy());
        String displayName = validator.validateMetadata(file);
        String oldPath = exercise.getStarterDocPath();
        OfficeStorageService.StagedDocument staged = storage.stage(file, displayName);
        OfficeStorageService.StoredDocument stored = null;
        try {
            validator.validateContainer(staged.path());
            parser.parse(staged.path());
            stored = storage.commit(staged);
            exercise.setStarterDocPath(storage.path(stored));
            exercise.setStarterDocName(displayName);
            exerciseMapper.updateById(exercise);
        } catch (OfficeDocumentException exception) {
            storage.discard(staged);
            if (stored != null) storage.delete(storage.path(stored));
            throw documentApiException(exception);
        } catch (RuntimeException exception) {
            storage.discard(staged);
            if (stored != null) storage.delete(storage.path(stored));
            throw exception;
        }
        if (oldPath != null && !oldPath.equals(storage.path(stored))) deleteFileIfUnused(oldPath);
        return Map.of("starterDocName", displayName);
    }

    public File getStarterDocFile(int exerciseId) {
        OfficeExerciseEntity exercise = findExercise(exerciseId);
        boolean manager = CurrentUser.canManage(exercise.getCreatedBy());
        if (!manager && (!Boolean.TRUE.equals(exercise.getVisible())
                || !ContentVisibility.PUBLIC.name().equals(exercise.getContentVisibility()))) {
            throw ApiException.notFound("起始文档不存在");
        }
        return requireStarterDoc(exercise);
    }

    public String getStarterDocName(int exerciseId) {
        OfficeExerciseEntity exercise = findExercise(exerciseId);
        boolean manager = CurrentUser.canManage(exercise.getCreatedBy());
        if (!manager && (!Boolean.TRUE.equals(exercise.getVisible())
                || !ContentVisibility.PUBLIC.name().equals(exercise.getContentVisibility()))) {
            throw ApiException.notFound("起始文档不存在");
        }
        return exercise.getStarterDocName();
    }

    public File requireStarterDoc(OfficeExerciseEntity exercise) {
        if (exercise.getStarterDocPath() == null || exercise.getStarterDocPath().isBlank()) {
            throw ApiException.notFound("起始文档不存在");
        }
        return requireStoredFile(exercise.getStarterDocPath(), "起始文档文件丢失");
    }

    public File getTeacherDocFile(int exerciseId) {
        OfficeExerciseEntity exercise = findExercise(exerciseId);
        if (!Boolean.TRUE.equals(exercise.getVisible()) && !CurrentUser.canManage(exercise.getCreatedBy())) {
            throw ApiException.notFound("参考文档不存在");
        }
        CurrentUser.requireCanManage(exercise.getCreatedBy());
        if (exercise.getTeacherDocPath() == null) throw ApiException.notFound("参考文档不存在");
        return requireStoredFile(exercise.getTeacherDocPath(), "参考文档文件丢失");
    }

    public String getTeacherDocName(int exerciseId) {
        OfficeExerciseEntity exercise = findExercise(exerciseId);
        CurrentUser.requireCanManage(exercise.getCreatedBy());
        return exercise.getTeacherDocName();
    }

    public OfficeSubmissionDtos.StudentSubmission submitDoc(int exerciseId, MultipartFile file) {
        Integer userId = CurrentUser.getId();
        if (userId == null) throw ApiException.unauthorized("请先登录");

        OfficeExerciseEntity exercise = findExercise(exerciseId);
        if (!Boolean.TRUE.equals(exercise.getVisible())
                || !ContentVisibility.PUBLIC.name().equals(exercise.getContentVisibility())) {
            throw ApiException.conflict("该练习已停用，无法继续提交");
        }
        return responseMapper.student(judgeDocument(exercise, file, userId, null));
    }

    public OfficeSubmissionDtos.StudentSubmission submitContestDoc(OfficeExerciseEntity exercise,
                                                                    MultipartFile file, long contestProblemId) {
        Integer userId = CurrentUser.getId();
        if (userId == null) throw ApiException.unauthorized("请先登录");
        if (!Boolean.TRUE.equals(exercise.getVisible())) {
            throw ApiException.conflict("该练习已停用，无法继续提交");
        }
        return responseMapper.student(judgeDocument(exercise, file, userId, contestProblemId));
    }

    private OfficeDocSubmissionEntity judgeDocument(OfficeExerciseEntity exercise, MultipartFile file,
                                                     int userId, Long contestProblemId) {
        int exerciseId = exercise.getId();
        if (exercise.getTeacherDocPath() == null || exercise.getTeacherDocPath().isBlank()) {
            throw ApiException.badRequest("该练习尚未上传老师参考文档，暂无法提交");
        }
        if (exercise.getStarterDocPath() == null || exercise.getStarterDocPath().isBlank()) {
            throw ApiException.badRequest("该练习尚未上传学生起始文档，暂无法提交");
        }

        String displayName = validator.validateMetadata(file);
        OfficeStorageService.StagedDocument staged = storage.stage(file, displayName);
        OfficeStorageService.StoredDocument stored = null;
        OfficeDocSubmissionEntity submission = new OfficeDocSubmissionEntity();
        submission.setUserId(userId);
        submission.setExerciseId(exerciseId);
        submission.setContestProblemId(contestProblemId);
        submission.setStudentDocName(displayName);
        submission.setStatus("PENDING");
        submission.setJudgeVersion(OfficeDocumentComparator.JUDGE_VERSION);
        submission.setResultDetail(Map.of("judgeVersion", OfficeDocumentComparator.JUDGE_VERSION));
        try {
            submissionMapper.insert(submission);
            long startedAt = System.nanoTime();
            OfficeDocumentModel student;
            OfficeDocumentModel teacher;
            OfficeJudgeResult result;
            Map<String, Object> structured;
            long parseMs;
            long compareMs;
            try (OfficeJudgeConcurrencyGate.Permit ignored = concurrencyGate.acquire()) {
                submission.setStatus("JUDGING");
                submissionMapper.updateById(submission);
                validator.validateContainer(staged.path());
                Path reference = storage.require(exercise.getTeacherDocPath());
                validator.validateContainer(reference);
                long parseStartedAt = System.nanoTime();
                student = parser.parse(staged.path());
                teacher = parser.parse(reference);
                parseMs = elapsedMillis(parseStartedAt);
                long compareStartedAt = System.nanoTime();
                result = comparator.compare(teacher, student);
                structured = resultSerializer.structured(result);
                compareMs = elapsedMillis(compareStartedAt);
                stored = storage.commit(staged);
            }

            submission.setStudentDocPath(storage.path(stored));
            submission.setAutoResult(resultSerializer.json(Map.of(
                    "paragraphCount", student.paragraphs().size(),
                    "tableCount", student.tables().size())));
            submission.setCompareResult(resultSerializer.comparisonRows(result));
            submission.setResultDetail(structured);
            submission.setJudgeVersion(result.judgeVersion());
            submission.setScore(result.earnedScore());
            submission.setStatus(result.passed() ? "COMPLETED" : "NEEDS_REVIEW");
            submission.setJudgedAt(java.time.LocalDateTime.now());
            submissionMapper.updateById(submission);
            log.info("Office judge completed submissionId={} exerciseId={} userId={} storageId={} judgeVersion={} score={} parseMs={} compareMs={} totalMs={}",
                    submission.getId(), exerciseId, userId, stored.storageId(),
                    result.judgeVersion(), result.earnedScore(), parseMs, compareMs, elapsedMillis(startedAt));
            return submission;
        } catch (OfficeDocumentException exception) {
            storage.discard(staged);
            if (stored != null) storage.delete(storage.path(stored));
            markFailed(submission, exception.category().name());
            log.warn("Office document rejected exerciseId={} userId={} category={}",
                    exerciseId, userId, exception.category());
            throw documentApiException(exception);
        } catch (ApiException exception) {
            storage.discard(staged);
            if (stored != null) storage.delete(storage.path(stored));
            markFailed(submission, "REQUEST_REJECTED");
            throw exception;
        } catch (Exception exception) {
            storage.discard(staged);
            if (stored != null) storage.delete(storage.path(stored));
            markFailed(submission, "JUDGE_INTERNAL_ERROR");
            log.error("Office judge failed exerciseId={} userId={} type={}",
                    exerciseId, userId, exception.getClass().getSimpleName());
            throw ApiException.badRequest("文档判题失败，请稍后重试");
        }
    }

    public OfficeSubmissionDtos.StudentSubmission getStudentSubmission(int submissionId) {
        OfficeDocSubmissionEntity submission = findSubmission(submissionId);
        requireCanAccessSubmission(submission);
        return responseMapper.student(submission);
    }

    public OfficeSubmissionDtos.ReviewerSubmission getReviewerSubmission(int submissionId) {
        OfficeDocSubmissionEntity submission = findSubmission(submissionId);
        requireCanReviewSubmission(submission);
        return responseMapper.reviewer(submission);
    }

    public OfficeSubmissionDtos.SubmissionListResponse listSubmissions(Integer exerciseId, int page, int pageSize) {
        Integer userId = CurrentUser.getId();
        if (userId == null) throw ApiException.unauthorized("请先登录");
        QueryWrapper<OfficeDocSubmissionEntity> query = new QueryWrapper<>();

        if (CurrentUser.isAdmin()) {
            if (exerciseId != null) query.eq("exercise_id", exerciseId);
        } else if (CurrentUser.isTeacher()) {
            if (exerciseId != null) {
                OfficeExerciseEntity exercise = findExercise(exerciseId);
                CurrentUser.requireCanManage(exercise.getCreatedBy());
                query.eq("exercise_id", exerciseId);
            } else {
                List<OfficeExerciseEntity> owned = exerciseMapper.selectList(
                        new QueryWrapper<OfficeExerciseEntity>().eq("created_by", userId).select("id"));
                if (owned.isEmpty()) {
                    query.eq("exercise_id", -1);
                } else {
                    query.in("exercise_id", owned.stream().map(OfficeExerciseEntity::getId).toList());
                }
            }
        } else {
            query.eq("user_id", userId);
            if (exerciseId != null) query.eq("exercise_id", exerciseId);
        }

        query.orderByDesc("id");
        Page<OfficeDocSubmissionEntity> result = submissionMapper.selectPage(new Page<>(page, pageSize), query);
        return new OfficeSubmissionDtos.SubmissionListResponse(
                result.getTotal(), page, pageSize,
                result.getRecords().stream().map(responseMapper::summary).toList());
    }

    public OfficeSubmissionDtos.ReviewerSubmission review(int submissionId, ReviewRequest request) {
        OfficeDocSubmissionEntity submission = findSubmission(submissionId);
        requireCanReviewSubmission(submission);
        submission.setScore(Math.max(0, Math.min(100, request.getScore())));
        submission.setTeacherComment(request.getComment() == null ? "" : request.getComment());
        submission.setStatus("REVIEWED");
        submissionMapper.updateById(submission);
        return responseMapper.reviewer(submission);
    }

    public File getStudentDocFile(int submissionId) {
        OfficeDocSubmissionEntity submission = findSubmission(submissionId);
        requireCanAccessSubmission(submission);
        return requireStoredFile(submission.getStudentDocPath(), "文档文件丢失");
    }

    public String getStudentDocName(int submissionId) {
        OfficeDocSubmissionEntity submission = findSubmission(submissionId);
        requireCanAccessSubmission(submission);
        return submission.getStudentDocName();
    }

    private Map<String, Object> listResponse(int page, int pageSize, QueryWrapper<OfficeExerciseEntity> query) {
        Page<OfficeExerciseEntity> result = exerciseMapper.selectPage(new Page<>(page, pageSize), query);
        List<OfficeExerciseEntity> exercises = result.getRecords();
        Map<Integer, String> creatorNames = loadCreatorNames(exercises);
        Map<Integer, Long> submissionCounts = loadSubmissionCounts(exercises);
        List<Map<String, Object>> items = exercises.stream().map(exercise -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", exercise.getId());
            item.put("title", exercise.getTitle());
            item.put("difficulty", exercise.getDifficulty());
            item.put("visible", exercise.getVisible());
            item.put("contentVisibility", exercise.getContentVisibility());
            item.put("hasTeacherDoc", exercise.getTeacherDocPath() != null && !exercise.getTeacherDocPath().isBlank());
            item.put("hasStarterDoc", exercise.getStarterDocPath() != null && !exercise.getStarterDocPath().isBlank());
            item.put("starterDocName", exercise.getStarterDocName());
            item.put("createdBy", exercise.getCreatedBy());
            item.put("creatorUsername", exercise.getCreatedBy() == null ? null : creatorNames.get(exercise.getCreatedBy()));
            item.put("submissionCount", submissionCounts.getOrDefault(exercise.getId(), 0L));
            item.put("createdAt", exercise.getCreatedAt());
            return item;
        }).toList();
        return Map.of("total", result.getTotal(), "page", page, "pageSize", pageSize, "exercises", items);
    }

    private void applyToEntity(OfficeExerciseEntity exercise, OfficeExerciseCreateRequest request) {
        exercise.setTitle(request.getTitle());
        exercise.setDifficulty(request.getDifficulty() == null || request.getDifficulty().isBlank()
                ? "EASY" : request.getDifficulty().toUpperCase());
        exercise.setDescription(request.getDescription());
        exercise.setVisible(request.getVisible() == null || request.getVisible());
        exercise.setContentVisibility(ContentVisibility.parse(request.getContentVisibility()).name());
    }

    private OfficeExerciseEntity findExercise(int id) {
        OfficeExerciseEntity exercise = exerciseMapper.selectById(id);
        if (exercise == null) throw ApiException.notFound("练习不存在");
        return exercise;
    }

    private OfficeDocSubmissionEntity findSubmission(int id) {
        OfficeDocSubmissionEntity submission = submissionMapper.selectById(id);
        if (submission == null) throw ApiException.notFound("提交记录不存在");
        return submission;
    }

    private void requireCanAccessSubmission(OfficeDocSubmissionEntity submission) {
        Integer userId = CurrentUser.getId();
        if (userId == null) throw ApiException.unauthorized("请先登录");
        if (Objects.equals(submission.getUserId(), userId) || CurrentUser.isAdmin()) return;
        if (CurrentUser.isTeacher()) {
            OfficeExerciseEntity exercise = findExercise(submission.getExerciseId());
            if (CurrentUser.canManage(exercise.getCreatedBy())) return;
        }
        throw ApiException.forbidden("无权查看此提交记录");
    }

    private void requireCanReviewSubmission(OfficeDocSubmissionEntity submission) {
        OfficeExerciseEntity exercise = findExercise(submission.getExerciseId());
        CurrentUser.requireCanManage(exercise.getCreatedBy());
    }

    private Map<Integer, String> loadCreatorNames(List<OfficeExerciseEntity> exercises) {
        Set<Integer> creatorIds = exercises.stream()
                .map(OfficeExerciseEntity::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (creatorIds.isEmpty()) return Map.of();
        Map<Integer, String> names = new HashMap<>();
        userMapper.selectBatchIds(creatorIds).forEach(user -> names.put(user.getId(), user.getUsername()));
        return names;
    }

    private Map<Integer, Long> loadSubmissionCounts(List<OfficeExerciseEntity> exercises) {
        Set<Integer> ids = exercises.stream().map(OfficeExerciseEntity::getId).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        List<OfficeDocSubmissionEntity> submissions = submissionMapper.selectList(
                new QueryWrapper<OfficeDocSubmissionEntity>().in("exercise_id", ids).select("exercise_id"));
        return submissions.stream().collect(Collectors.groupingBy(
                OfficeDocSubmissionEntity::getExerciseId, Collectors.counting()));
    }

    private String loadCreatorUsername(Integer createdBy) {
        if (createdBy == null) return null;
        var user = userMapper.selectById(createdBy);
        return user == null ? null : user.getUsername();
    }

    private File requireStoredFile(String storedPath, String missingMessage) {
        try {
            return storage.require(storedPath).toFile();
        } catch (OfficeDocumentException exception) {
            throw ApiException.notFound(missingMessage);
        }
    }

    private boolean deleteFileIfUnused(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) return false;
        long exerciseRefs = exerciseMapper.selectCount(
                new QueryWrapper<OfficeExerciseEntity>()
                        .eq("teacher_doc_path", storedPath).or().eq("starter_doc_path", storedPath));
        long submissionRefs = submissionMapper.selectCount(
                new QueryWrapper<OfficeDocSubmissionEntity>().eq("student_doc_path", storedPath));
        if (exerciseRefs > 0 || submissionRefs > 0) return false;

        return storage.delete(storedPath);
    }

    private long elapsedMillis(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private ApiException documentApiException(OfficeDocumentException exception) {
        return ApiException.badRequest(exception.category().clientMessage());
    }

    private void markFailed(OfficeDocSubmissionEntity submission, String category) {
        if (submission == null || submission.getId() == null) return;
        try {
            submission.setStudentDocPath(null);
            submission.setStatus("FAILED");
            submission.setScore(null);
            submission.setErrorCategory(category);
            submission.setJudgedAt(java.time.LocalDateTime.now());
            submission.setResultDetail(Map.of(
                    "judgeVersion", OfficeDocumentComparator.JUDGE_VERSION,
                    "status", "FAILED",
                    "errorCategory", category));
            submissionMapper.updateById(submission);
        } catch (RuntimeException persistenceFailure) {
            log.error("Unable to persist failed Office judge state submissionId={} type={}",
                    submission.getId(), persistenceFailure.getClass().getSimpleName());
        }
    }
}

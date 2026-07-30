package com.oj.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import com.oj.common.DocComparator;
import com.oj.common.DocxParser;
import com.oj.config.AppProperties;
import com.oj.dto.OfficeExerciseCreateRequest;
import com.oj.dto.ReviewRequest;
import com.oj.entity.OfficeDocSubmissionEntity;
import com.oj.entity.OfficeExerciseEntity;
import com.oj.mapper.OfficeDocSubmissionMapper;
import com.oj.mapper.OfficeExerciseMapper;
import com.oj.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OfficeDocService {

    private static final Logger log = LoggerFactory.getLogger(OfficeDocService.class);

    private final OfficeExerciseMapper exerciseMapper;
    private final OfficeDocSubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OfficeDocService(OfficeExerciseMapper exerciseMapper,
                            OfficeDocSubmissionMapper submissionMapper,
                            UserMapper userMapper,
                            AppProperties appProperties) {
        this.exerciseMapper = exerciseMapper;
        this.submissionMapper = submissionMapper;
        this.userMapper = userMapper;
        this.appProperties = appProperties;
    }

    public Map<String, Object> listExercises(int page, int pageSize) {
        QueryWrapper<OfficeExerciseEntity> query = new QueryWrapper<>();
        query.eq("visible", true).orderByDesc("id");
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
        if (!Boolean.TRUE.equals(exercise.getVisible()) && !CurrentUser.canManage(exercise.getCreatedBy())) {
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

        int deletedSubmissions = submissionMapper.delete(
                new QueryWrapper<OfficeDocSubmissionEntity>().eq("exercise_id", id));
        exerciseMapper.deleteById(id);

        int deletedFiles = 0;
        for (String path : candidatePaths) {
            if (deleteFileIfUnused(path)) deletedFiles++;
        }
        return Map.of(
                "deleted", true,
                "deletedSubmissions", deletedSubmissions,
                "deletedFiles", deletedFiles,
                "affectedUsers", affectedUserIds.size()
        );
    }

    public Map<String, Object> uploadTeacherDoc(int exerciseId, MultipartFile file) {
        OfficeExerciseEntity exercise = findExercise(exerciseId);
        CurrentUser.requireCanManage(exercise.getCreatedBy());
        String oldPath = exercise.getTeacherDocPath();
        String savedPath = saveDocx(file, "teacher_" + exerciseId);
        try {
            exercise.setTeacherDocPath(savedPath);
            exercise.setTeacherDocName(file.getOriginalFilename());
            exerciseMapper.updateById(exercise);
        } catch (RuntimeException exception) {
            deleteFileIfUnused(savedPath);
            throw exception;
        }
        if (oldPath != null && !oldPath.equals(savedPath)) deleteFileIfUnused(oldPath);
        return Map.of("teacherDocName", file.getOriginalFilename(), "path", savedPath);
    }

    public File getTeacherDocFile(int exerciseId) {
        OfficeExerciseEntity exercise = findExercise(exerciseId);
        if (!Boolean.TRUE.equals(exercise.getVisible()) && !CurrentUser.canManage(exercise.getCreatedBy())) {
            throw ApiException.notFound("参考文档不存在");
        }
        if (exercise.getTeacherDocPath() == null) throw ApiException.notFound("参考文档不存在");
        return requireStoredFile(exercise.getTeacherDocPath(), "参考文档文件丢失");
    }

    public String getTeacherDocName(int exerciseId) {
        OfficeExerciseEntity exercise = findExercise(exerciseId);
        if (!Boolean.TRUE.equals(exercise.getVisible()) && !CurrentUser.canManage(exercise.getCreatedBy())) {
            throw ApiException.notFound("参考文档不存在");
        }
        return exercise.getTeacherDocName();
    }

    public OfficeDocSubmissionEntity submitDoc(int exerciseId, MultipartFile file) {
        Integer userId = CurrentUser.getId();
        if (userId == null) throw ApiException.unauthorized("请先登录");

        OfficeExerciseEntity exercise = findExercise(exerciseId);
        if (!Boolean.TRUE.equals(exercise.getVisible())) {
            throw ApiException.conflict("该练习已停用，无法继续提交");
        }
        if (exercise.getTeacherDocPath() == null || exercise.getTeacherDocPath().isBlank()) {
            throw ApiException.badRequest("该练习尚未上传老师参考文档，暂无法提交");
        }

        String savedPath = saveDocx(file, "student_" + userId + "_" + exerciseId);
        try {
            List<Map<String, Object>> studentParagraphs = DocxParser.parse(savedPath);
            List<Map<String, Object>> teacherParagraphs = DocxParser.parse(exercise.getTeacherDocPath());
            List<Map<String, Object>> compareResult = DocComparator.compare(studentParagraphs, teacherParagraphs);
            int matchPercent = DocComparator.matchPercent(compareResult);

            OfficeDocSubmissionEntity submission = new OfficeDocSubmissionEntity();
            submission.setUserId(userId);
            submission.setExerciseId(exerciseId);
            submission.setStudentDocPath(savedPath);
            submission.setStudentDocName(file.getOriginalFilename());
            submission.setAutoResult(toJson(studentParagraphs));
            submission.setCompareResult(toJson(compareResult));
            submission.setStatus(matchPercent == 100 ? "AUTO_CHECKED" : "NEEDS_REVIEW");
            submissionMapper.insert(submission);
            return submission;
        } catch (ApiException exception) {
            deleteFileIfUnused(savedPath);
            throw exception;
        } catch (Exception exception) {
            deleteFileIfUnused(savedPath);
            throw ApiException.badRequest("文档解析或比对失败：" + exception.getMessage());
        }
    }

    public OfficeDocSubmissionEntity getSubmission(int submissionId) {
        OfficeDocSubmissionEntity submission = findSubmission(submissionId);
        requireCanAccessSubmission(submission);
        return submission;
    }

    public Map<String, Object> listSubmissions(Integer exerciseId, int page, int pageSize) {
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
        List<Map<String, Object>> items = result.getRecords().stream().map(submission -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", submission.getId());
            item.put("exerciseId", submission.getExerciseId());
            item.put("userId", submission.getUserId());
            item.put("studentDocName", submission.getStudentDocName());
            item.put("status", submission.getStatus());
            item.put("score", submission.getScore());
            item.put("createdAt", submission.getCreatedAt());
            return item;
        }).toList();
        return Map.of("total", result.getTotal(), "page", page, "pageSize", pageSize, "submissions", items);
    }

    public OfficeDocSubmissionEntity review(int submissionId, ReviewRequest request) {
        OfficeDocSubmissionEntity submission = findSubmission(submissionId);
        OfficeExerciseEntity exercise = findExercise(submission.getExerciseId());
        CurrentUser.requireCanManage(exercise.getCreatedBy());
        submission.setScore(Math.max(0, Math.min(100, request.getScore())));
        submission.setTeacherComment(request.getComment() == null ? "" : request.getComment());
        submission.setStatus("REVIEWED");
        submissionMapper.updateById(submission);
        return submission;
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
            item.put("hasTeacherDoc", exercise.getTeacherDocPath() != null && !exercise.getTeacherDocPath().isBlank());
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

    private String saveDocx(MultipartFile file, String prefix) {
        try {
            Path root = storageRoot();
            Files.createDirectories(root);
            String original = file.getOriginalFilename() == null ? "upload.docx" : file.getOriginalFilename();
            String safeOriginal = original.replaceAll("[^a-zA-Z0-9._\\-]", "_");
            if (!safeOriginal.toLowerCase().endsWith(".docx")) safeOriginal += ".docx";
            Path target = root.resolve(prefix + "_" + UUID.randomUUID() + "_" + safeOriginal).normalize();
            if (!target.startsWith(root)) throw ApiException.badRequest("文件路径不安全");
            file.transferTo(target.toFile());
            return target.toString();
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw ApiException.badRequest("文件保存失败：" + exception.getMessage());
        }
    }

    private File requireStoredFile(String storedPath, String missingMessage) {
        Path path = safeStoragePath(storedPath);
        if (path == null || !Files.isRegularFile(path) || Files.isSymbolicLink(path)) {
            throw ApiException.notFound(missingMessage);
        }
        return path.toFile();
    }

    private boolean deleteFileIfUnused(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) return false;
        long exerciseRefs = exerciseMapper.selectCount(
                new QueryWrapper<OfficeExerciseEntity>().eq("teacher_doc_path", storedPath));
        long submissionRefs = submissionMapper.selectCount(
                new QueryWrapper<OfficeDocSubmissionEntity>().eq("student_doc_path", storedPath));
        if (exerciseRefs > 0 || submissionRefs > 0) return false;

        Path path = safeStoragePath(storedPath);
        if (path == null || Files.isSymbolicLink(path)) {
            log.warn("Refusing to delete unsafe document path: {}", storedPath);
            return false;
        }
        try {
            if (Files.exists(path) && !Files.isRegularFile(path)) {
                log.warn("Refusing to delete non-file document path: {}", path);
                return false;
            }
            return Files.deleteIfExists(path);
        } catch (Exception exception) {
            log.warn("Failed to delete document file {}: {}", path, exception.getMessage());
            return false;
        }
    }

    private Path safeStoragePath(String storedPath) {
        try {
            Path root = storageRoot();
            Path path = Paths.get(storedPath).toAbsolutePath().normalize();
            if (path.equals(root) || !path.startsWith(root)) return null;
            return path;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Path storageRoot() {
        return Paths.get(appProperties.getDocStorage()).toAbsolutePath().normalize();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "[]";
        }
    }
}

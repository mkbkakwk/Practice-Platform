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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class OfficeDocService {

    private final OfficeExerciseMapper exerciseMapper;
    private final OfficeDocSubmissionMapper submissionMapper;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OfficeDocService(OfficeExerciseMapper exerciseMapper,
                            OfficeDocSubmissionMapper submissionMapper,
                            AppProperties appProperties) {
        this.exerciseMapper = exerciseMapper;
        this.submissionMapper = submissionMapper;
        this.appProperties = appProperties;
    }

    // ---- exercises ----

    public Map<String, Object> listExercises(int page, int pageSize) {
        QueryWrapper<OfficeExerciseEntity> qw = new QueryWrapper<>();
        if (!CurrentUser.isAdmin()) qw.eq("visible", true);
        qw.orderByDesc("id");
        Page<OfficeExerciseEntity> p = exerciseMapper.selectPage(new Page<>(page, pageSize), qw);
        List<Map<String, Object>> items = p.getRecords().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("title", e.getTitle());
            m.put("difficulty", e.getDifficulty());
            m.put("visible", e.getVisible());
            m.put("hasTeacherDoc", e.getTeacherDocPath() != null && !e.getTeacherDocPath().isBlank());
            m.put("createdAt", e.getCreatedAt());
            return m;
        }).toList();
        return Map.of("total", p.getTotal(), "page", page, "pageSize", pageSize, "exercises", items);
    }

    public OfficeExerciseEntity getExercise(int id) {
        OfficeExerciseEntity e = exerciseMapper.selectById(id);
        if (e == null || (!Boolean.TRUE.equals(e.getVisible()) && !CurrentUser.isAdmin())) {
            throw ApiException.notFound("练习不存在");
        }
        return e;
    }

    public OfficeExerciseEntity createExercise(OfficeExerciseCreateRequest req) {
        OfficeExerciseEntity e = new OfficeExerciseEntity();
        e.setTitle(req.getTitle());
        e.setDifficulty(req.getDifficulty() == null || req.getDifficulty().isBlank() ? "EASY" : req.getDifficulty().toUpperCase());
        e.setDescription(req.getDescription());
        e.setVisible(req.getVisible() == null || req.getVisible());
        exerciseMapper.insert(e);
        return e;
    }

    /** Admin uploads the teacher's reference .docx. Parses and stores it. */
    public Map<String, Object> uploadTeacherDoc(int exerciseId, MultipartFile file) {
        OfficeExerciseEntity e = exerciseMapper.selectById(exerciseId);
        if (e == null) throw ApiException.notFound("练习不存在");
        String savedPath = saveDocx(file, "teacher_" + exerciseId);
        e.setTeacherDocPath(savedPath);
        e.setTeacherDocName(file.getOriginalFilename());
        exerciseMapper.updateById(e);
        return Map.of("teacherDocName", file.getOriginalFilename(), "path", savedPath);
    }

    /** Download the teacher's reference doc (for students to see requirements). */
    public File getTeacherDocFile(int exerciseId) {
        OfficeExerciseEntity e = exerciseMapper.selectById(exerciseId);
        if (e == null || e.getTeacherDocPath() == null) throw ApiException.notFound("参考文档不存在");
        File f = new File(e.getTeacherDocPath());
        if (!f.exists()) throw ApiException.notFound("参考文档文件丢失");
        return f;
    }

    public String getTeacherDocName(int exerciseId) {
        OfficeExerciseEntity e = exerciseMapper.selectById(exerciseId);
        return e != null ? e.getTeacherDocName() : null;
    }

    // ---- student submission ----

    /**
     * Student uploads a .docx. The system:
     * 1. Parses the student's doc (程序自检)
     * 2. Compares against the teacher's reference doc (和老师文档对比)
     * 3. Saves the submission with results. Status = NEEDS_REVIEW if any diff found.
     */
    public OfficeDocSubmissionEntity submitDoc(int exerciseId, MultipartFile file) {
        Integer userId = CurrentUser.getId();
        if (userId == null) throw ApiException.unauthorized("请先登录");

        OfficeExerciseEntity ex = exerciseMapper.selectById(exerciseId);
        if (ex == null || (!Boolean.TRUE.equals(ex.getVisible()) && !CurrentUser.isAdmin())) {
            throw ApiException.notFound("练习不存在");
        }
        if (ex.getTeacherDocPath() == null || ex.getTeacherDocPath().isBlank()) {
            throw ApiException.badRequest("该练习尚未上传老师参考文档，暂无法提交");
        }

        // 1. Save student doc
        String savedPath = saveDocx(file, "student_" + userId + "_" + exerciseId);

        // 2. Parse student doc (程序自检)
        List<Map<String, Object>> studentParas;
        try {
            studentParas = DocxParser.parse(savedPath);
        } catch (Exception e) {
            throw ApiException.badRequest("文档解析失败，请确认上传的是 .docx 格式");
        }

        // 3. Parse teacher doc (cached parse) and compare (和老师文档对比)
        List<Map<String, Object>> compareResult;
        try {
            List<Map<String, Object>> teacherParas = DocxParser.parse(ex.getTeacherDocPath());
            compareResult = DocComparator.compare(studentParas, teacherParas);
        } catch (Exception e) {
            throw ApiException.badRequest("老师参考文档解析失败：" + e.getMessage());
        }

        int matchPercent = DocComparator.matchPercent(compareResult);
        String status = matchPercent == 100 ? "AUTO_CHECKED" : "NEEDS_REVIEW";

        // 4. Save submission
        OfficeDocSubmissionEntity sub = new OfficeDocSubmissionEntity();
        sub.setUserId(userId);
        sub.setExerciseId(exerciseId);
        sub.setStudentDocPath(savedPath);
        sub.setStudentDocName(file.getOriginalFilename());
        sub.setAutoResult(toJson(studentParas));
        sub.setCompareResult(toJson(compareResult));
        sub.setStatus(status);
        submissionMapper.insert(sub);
        return sub;
    }

    public OfficeDocSubmissionEntity getSubmission(int submissionId) {
        OfficeDocSubmissionEntity s = submissionMapper.selectById(submissionId);
        if (s == null) throw ApiException.notFound("提交记录不存在");
        // Students see only their own; teachers/admins can view any (for review).
        Integer uid = CurrentUser.getId();
        if (uid == null || (!s.getUserId().equals(uid) && !CurrentUser.isTeacherOrAdmin())) {
            throw ApiException.forbidden("无权查看此提交记录");
        }
        return s;
    }

    /** List submissions: teachers/admins see all (optionally filtered by exercise);
     *  students see only their own. */
    public Map<String, Object> listSubmissions(Integer exerciseId, int page, int pageSize) {
        QueryWrapper<OfficeDocSubmissionEntity> qw = new QueryWrapper<>();
        if (exerciseId != null) qw.eq("exercise_id", exerciseId);
        if (!CurrentUser.isTeacherOrAdmin()) qw.eq("user_id", CurrentUser.getId());
        qw.orderByDesc("id");
        Page<OfficeDocSubmissionEntity> p = submissionMapper.selectPage(new Page<>(page, pageSize), qw);
        List<Map<String, Object>> items = p.getRecords().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("exerciseId", s.getExerciseId());
            m.put("userId", s.getUserId());
            m.put("studentDocName", s.getStudentDocName());
            m.put("status", s.getStatus());
            m.put("score", s.getScore());
            m.put("createdAt", s.getCreatedAt());
            return m;
        }).toList();
        return Map.of("total", p.getTotal(), "page", page, "pageSize", pageSize, "submissions", items);
    }

    /** Teacher reviews a submission: sets score + comment, marks REVIEWED. */
    public OfficeDocSubmissionEntity review(int submissionId, ReviewRequest req) {
        OfficeDocSubmissionEntity s = submissionMapper.selectById(submissionId);
        if (s == null) throw ApiException.notFound("提交记录不存在");
        s.setScore(Math.max(0, Math.min(100, req.getScore())));
        s.setTeacherComment(req.getComment() == null ? "" : req.getComment());
        s.setStatus("REVIEWED");
        submissionMapper.updateById(s);
        return s;
    }

    /** Get the student's uploaded doc file for download/viewing. */
    public File getStudentDocFile(int submissionId) {
        OfficeDocSubmissionEntity s = submissionMapper.selectById(submissionId);
        if (s == null) throw ApiException.notFound("提交记录不存在");
        Integer uid = CurrentUser.getId();
        if (uid == null || (!s.getUserId().equals(uid) && !CurrentUser.isTeacherOrAdmin())) {
            throw ApiException.forbidden("无权下载此文档");
        }
        File f = new File(s.getStudentDocPath());
        if (!f.exists()) throw ApiException.notFound("文档文件丢失");
        return f;
    }

    public String getStudentDocName(int submissionId) {
        OfficeDocSubmissionEntity s = submissionMapper.selectById(submissionId);
        return s != null ? s.getStudentDocName() : null;
    }

    // ---- helpers ----

    private String saveDocx(MultipartFile file, String prefix) {
        String dir = appProperties.getDocStorage();
        try {
            Path dirPath = Paths.get(dir);
            Files.createDirectories(dirPath);
            String original = file.getOriginalFilename() == null ? "upload.docx" : file.getOriginalFilename();
            String ext = original.toLowerCase().endsWith(".docx") ? "" : ".docx";
            String filename = prefix + "_" + System.currentTimeMillis() + "_" + original.replaceAll("[^a-zA-Z0-9._\\-]", "_") + ext;
            Path target = dirPath.resolve(filename);
            file.transferTo(target.toFile());
            return target.toAbsolutePath().toString();
        } catch (Exception e) {
            throw ApiException.badRequest("文件保存失败：" + e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}

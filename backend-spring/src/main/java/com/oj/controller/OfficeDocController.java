package com.oj.controller;

import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import com.oj.dto.OfficeExerciseCreateRequest;
import com.oj.dto.ReviewRequest;
import com.oj.entity.OfficeDocSubmissionEntity;
import com.oj.entity.OfficeExerciseEntity;
import com.oj.service.OfficeDocService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/office/docs")
public class OfficeDocController {

    private final OfficeDocService service;

    public OfficeDocController(OfficeDocService service) {
        this.service = service;
    }

    // ---- exercises ----

    @GetMapping("/exercises")
    public Map<String, Object> listExercises(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        page = Math.max(1, page);
        pageSize = Math.min(50, Math.max(1, pageSize));
        return service.listExercises(page, pageSize);
    }

    @GetMapping("/exercises/{id}")
    public Map<String, Object> getExercise(@PathVariable int id) {
        OfficeExerciseEntity e = service.getExercise(id);
        return Map.of("exercise", e);
    }

    @PostMapping("/exercises")
    public ResponseEntity<Map<String, Object>> createExercise(@Valid @RequestBody OfficeExerciseCreateRequest req) {
        assertTeacherOrAdmin();
        OfficeExerciseEntity e = service.createExercise(req);
        return ResponseEntity.ok(Map.of("exercise", e));
    }

    @PostMapping("/exercises/{id}/teacher-doc")
    public Map<String, Object> uploadTeacherDoc(@PathVariable int id, @RequestParam("file") MultipartFile file) {
        assertTeacherOrAdmin();
        validateDocx(file);
        return service.uploadTeacherDoc(id, file);
    }

    @GetMapping("/exercises/{id}/teacher-doc")
    public ResponseEntity<FileSystemResource> downloadTeacherDoc(@PathVariable int id) {
        File f = service.getTeacherDocFile(id);
        String name = service.getTeacherDocName(id);
        return fileResponse(f, name);
    }

    // ---- student submission ----

    @PostMapping("/exercises/{id}/submit")
    public Map<String, Object> submitDoc(@PathVariable int id, @RequestParam("file") MultipartFile file) {
        validateDocx(file);
        OfficeDocSubmissionEntity s = service.submitDoc(id, file);
        return Map.of("submission", s);
    }

    @GetMapping("/submissions/{id}")
    public Map<String, Object> getSubmission(@PathVariable int id) {
        OfficeDocSubmissionEntity s = service.getSubmission(id);
        return Map.of("submission", s);
    }

    @GetMapping("/submissions")
    public Map<String, Object> listSubmissions(
            @RequestParam(required = false) Integer exerciseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        page = Math.max(1, page);
        pageSize = Math.min(50, Math.max(1, pageSize));
        return service.listSubmissions(exerciseId, page, pageSize);
    }

    @GetMapping("/submissions/{id}/download")
    public ResponseEntity<FileSystemResource> downloadStudentDoc(@PathVariable int id) {
        File f = service.getStudentDocFile(id);
        String name = service.getStudentDocName(id);
        return fileResponse(f, name);
    }

    @PutMapping("/submissions/{id}/review")
    public Map<String, Object> review(@PathVariable int id, @Valid @RequestBody ReviewRequest req) {
        assertTeacherOrAdmin();
        OfficeDocSubmissionEntity s = service.review(id, req);
        return Map.of("submission", s);
    }

    // ---- helpers ----

    private void assertTeacherOrAdmin() {
        if (!CurrentUser.isTeacherOrAdmin()) {
            throw ApiException.forbidden("需要老师或管理员权限");
        }
    }

    private void assertAdmin() {
        if (!CurrentUser.isAdmin()) {
            throw ApiException.forbidden("需要管理员权限");
        }
    }

    private void validateDocx(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".docx")) {
            throw ApiException.badRequest("请上传 .docx 格式的 Word 文档");
        }
        if (file.isEmpty()) {
            throw ApiException.badRequest("文件为空");
        }
    }

    private ResponseEntity<FileSystemResource> fileResponse(File file, String filename) {
        FileSystemResource resource = new FileSystemResource(file);
        String encoded = URLEncoder.encode(filename == null ? file.getName() : filename, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(resource);
    }
}

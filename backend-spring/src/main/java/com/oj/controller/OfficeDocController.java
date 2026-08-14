package com.oj.controller;

import com.oj.dto.OfficeExerciseCreateRequest;
import com.oj.dto.OfficeSubmissionDtos;
import com.oj.dto.ReviewRequest;
import com.oj.dto.VisibilityRequest;
import com.oj.entity.OfficeExerciseEntity;
import com.oj.service.OfficeDocService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

    @GetMapping("/exercises")
    public Map<String, Object> listExercises(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        page = Math.max(1, page);
        pageSize = Math.min(50, Math.max(1, pageSize));
        return service.listExercises(page, pageSize);
    }

    @GetMapping("/exercises/manage")
    public Map<String, Object> listExercisesManage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        page = Math.max(1, page);
        pageSize = Math.min(50, Math.max(1, pageSize));
        return service.listExercisesManage(page, pageSize);
    }

    @GetMapping("/exercises/{id}")
    public Map<String, Object> getExercise(@PathVariable int id) {
        return Map.of("exercise", service.getExercise(id));
    }

    @PostMapping("/exercises")
    public ResponseEntity<Map<String, Object>> createExercise(
            @Valid @RequestBody OfficeExerciseCreateRequest request) {
        OfficeExerciseEntity exercise = service.createExercise(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("exercise", exercise));
    }

    @PutMapping("/exercises/{id}")
    public Map<String, Object> updateExercise(
            @PathVariable int id, @Valid @RequestBody OfficeExerciseCreateRequest request) {
        return Map.of("exercise", service.updateExercise(id, request));
    }

    @PutMapping("/exercises/{id}/visibility")
    public Map<String, Object> setVisibility(
            @PathVariable int id, @Valid @RequestBody VisibilityRequest request) {
        return Map.of("exercise", service.setVisible(id, request.getVisible()));
    }

    @DeleteMapping("/exercises/{id}")
    public Map<String, Object> hardDelete(@PathVariable int id) {
        return service.hardDelete(id);
    }

    @PostMapping("/exercises/{id}/teacher-doc")
    public Map<String, Object> uploadTeacherDoc(
            @PathVariable int id, @RequestParam("file") MultipartFile file) {
        return service.uploadTeacherDoc(id, file);
    }

    @GetMapping("/exercises/{id}/teacher-doc")
    public ResponseEntity<FileSystemResource> downloadTeacherDoc(@PathVariable int id) {
        File file = service.getTeacherDocFile(id);
        return fileResponse(file, service.getTeacherDocName(id));
    }

    @PostMapping("/exercises/{id}/submit")
    public OfficeSubmissionDtos.StudentSubmissionResponse submitDoc(
            @PathVariable int id, @RequestParam("file") MultipartFile file) {
        return new OfficeSubmissionDtos.StudentSubmissionResponse(service.submitDoc(id, file));
    }

    @GetMapping("/submissions/{id}")
    public OfficeSubmissionDtos.StudentSubmissionResponse getSubmission(@PathVariable int id) {
        return new OfficeSubmissionDtos.StudentSubmissionResponse(service.getStudentSubmission(id));
    }

    @GetMapping("/submissions/{id}/review-detail")
    public OfficeSubmissionDtos.ReviewerSubmissionResponse getReviewerSubmission(@PathVariable int id) {
        return new OfficeSubmissionDtos.ReviewerSubmissionResponse(service.getReviewerSubmission(id));
    }

    @GetMapping("/submissions")
    public OfficeSubmissionDtos.SubmissionListResponse listSubmissions(
            @RequestParam(required = false) Integer exerciseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        page = Math.max(1, page);
        pageSize = Math.min(50, Math.max(1, pageSize));
        return service.listSubmissions(exerciseId, page, pageSize);
    }

    @GetMapping("/submissions/{id}/download")
    public ResponseEntity<FileSystemResource> downloadStudentDoc(@PathVariable int id) {
        File file = service.getStudentDocFile(id);
        return fileResponse(file, service.getStudentDocName(id));
    }

    @PutMapping("/submissions/{id}/review")
    public OfficeSubmissionDtos.ReviewerSubmissionResponse review(
            @PathVariable int id, @Valid @RequestBody ReviewRequest request) {
        return new OfficeSubmissionDtos.ReviewerSubmissionResponse(service.review(id, request));
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

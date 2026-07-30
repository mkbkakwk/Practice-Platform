package com.oj.controller;

import com.oj.dto.ProblemDetail;
import com.oj.dto.ProblemListItem;
import com.oj.dto.ProblemUpsertRequest;
import com.oj.dto.VisibilityRequest;
import com.oj.entity.ProblemEntity;
import com.oj.service.ProblemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/problems")
public class ProblemController {

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String difficulty) {
        page = Math.max(1, page);
        pageSize = Math.min(50, Math.max(1, pageSize));
        List<ProblemListItem> problems = problemService.list(page, pageSize, difficulty);
        return Map.of("total", problemService.count(difficulty), "page", page, "pageSize", pageSize, "problems", problems);
    }

    @GetMapping("/manage")
    public Map<String, Object> listManage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String difficulty) {
        page = Math.max(1, page);
        pageSize = Math.min(50, Math.max(1, pageSize));
        List<ProblemListItem> problems = problemService.listManage(page, pageSize, difficulty);
        return Map.of("total", problemService.countManage(difficulty), "page", page, "pageSize", pageSize, "problems", problems);
    }

    @GetMapping("/{slug}")
    public Map<String, Object> getBySlug(@PathVariable String slug) {
        ProblemDetail problem = problemService.getBySlug(slug);
        return Map.of("problem", problem);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody ProblemUpsertRequest request) {
        ProblemEntity entity = problemService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("problem", entity));
    }

    @PutMapping("/{slug}")
    public Map<String, Object> update(@PathVariable String slug, @Valid @RequestBody ProblemUpsertRequest request) {
        return Map.of("problem", problemService.update(slug, request));
    }

    @PutMapping("/{slug}/visibility")
    public Map<String, Object> setVisibility(@PathVariable String slug, @Valid @RequestBody VisibilityRequest request) {
        return Map.of("problem", problemService.setVisible(slug, request.getVisible()));
    }

    @DeleteMapping("/{slug}")
    public Map<String, Object> hardDelete(@PathVariable String slug) {
        return problemService.hardDelete(slug);
    }
}

package com.oj.controller;

import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import com.oj.dto.ProblemDetail;
import com.oj.dto.ProblemListItem;
import com.oj.dto.ProblemUpsertRequest;
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
        long total = problemService.count(difficulty);
        return Map.of("total", total, "page", page, "pageSize", pageSize, "problems", problems);
    }

    @GetMapping("/{slug}")
    public Map<String, Object> getBySlug(@PathVariable String slug) {
        ProblemDetail problem = problemService.getBySlug(slug);
        return Map.of("problem", problem);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody ProblemUpsertRequest req) {
        assertAdmin();
        ProblemEntity e = problemService.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("problem", e));
    }

    @PutMapping("/{slug}")
    public Map<String, Object> update(@PathVariable String slug, @Valid @RequestBody ProblemUpsertRequest req) {
        assertAdmin();
        ProblemEntity e = problemService.update(slug, req);
        return Map.of("problem", e);
    }

    private void assertAdmin() {
        if (!CurrentUser.isAdmin()) {
            throw ApiException.forbidden("需要管理员权限");
        }
    }
}

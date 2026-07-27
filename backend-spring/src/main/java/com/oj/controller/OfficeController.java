package com.oj.controller;

import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import com.oj.dto.OfficeQuestionDetail;
import com.oj.dto.OfficeQuestionListItem;
import com.oj.dto.OfficeQuestionUpsertRequest;
import com.oj.dto.OfficeStats;
import com.oj.dto.OfficeSubmitRequest;
import com.oj.dto.OfficeSubmitResult;
import com.oj.entity.OfficeQuestionEntity;
import com.oj.service.OfficeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/office")
public class OfficeController {

    private final OfficeService officeService;

    public OfficeController(OfficeService officeService) {
        this.officeService = officeService;
    }

    @GetMapping("/questions")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String appType,
            @RequestParam(required = false) String difficulty) {
        page = Math.max(1, page);
        pageSize = Math.min(50, Math.max(1, pageSize));
        List<OfficeQuestionListItem> items = officeService.list(page, pageSize, appType, difficulty);
        long total = officeService.count(appType, difficulty);
        return Map.of("total", total, "page", page, "pageSize", pageSize, "questions", items);
    }

    @GetMapping("/questions/{id}")
    public Map<String, Object> getById(@PathVariable int id) {
        OfficeQuestionDetail q = officeService.getById(id);
        return Map.of("question", q);
    }

    @PostMapping("/questions")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody OfficeQuestionUpsertRequest req) {
        assertAdmin();
        OfficeQuestionEntity e = officeService.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("question", e));
    }

    @PutMapping("/questions/{id}")
    public Map<String, Object> update(@PathVariable int id, @Valid @RequestBody OfficeQuestionUpsertRequest req) {
        assertAdmin();
        OfficeQuestionEntity e = officeService.update(id, req);
        return Map.of("question", e);
    }

    @PostMapping("/submit")
    public Map<String, Object> submit(@Valid @RequestBody OfficeSubmitRequest req) {
        OfficeSubmitResult result = officeService.submit(req);
        return Map.of("result", result);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        OfficeStats stats = officeService.stats();
        return Map.of("stats", stats);
    }

    private void assertAdmin() {
        if (!CurrentUser.isAdmin()) {
            throw ApiException.forbidden("需要管理员权限");
        }
    }
}

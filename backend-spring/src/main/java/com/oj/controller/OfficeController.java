package com.oj.controller;

import com.oj.dto.*;
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
        return Map.of("total", officeService.count(appType, difficulty), "page", page,
                "pageSize", pageSize, "questions", items);
    }

    @GetMapping("/questions/manage")
    public Map<String, Object> listManage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String appType,
            @RequestParam(required = false) String difficulty) {
        page = Math.max(1, page);
        pageSize = Math.min(50, Math.max(1, pageSize));
        List<OfficeQuestionListItem> items = officeService.listManage(page, pageSize, appType, difficulty);
        return Map.of("total", officeService.countManage(appType, difficulty), "page", page,
                "pageSize", pageSize, "questions", items);
    }

    @GetMapping("/questions/{id}")
    public Map<String, Object> getById(@PathVariable int id) {
        return Map.of("question", officeService.getById(id));
    }

    @PostMapping("/questions")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody OfficeQuestionUpsertRequest request) {
        OfficeQuestionEntity entity = officeService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("question", entity));
    }

    @PutMapping("/questions/{id}")
    public Map<String, Object> update(@PathVariable int id, @Valid @RequestBody OfficeQuestionUpsertRequest request) {
        return Map.of("question", officeService.update(id, request));
    }

    @PutMapping("/questions/{id}/visibility")
    public Map<String, Object> setVisibility(@PathVariable int id, @Valid @RequestBody VisibilityRequest request) {
        return Map.of("question", officeService.setVisible(id, request.getVisible()));
    }

    @DeleteMapping("/questions/{id}")
    public Map<String, Object> hardDelete(@PathVariable int id) {
        return officeService.hardDelete(id);
    }

    @PostMapping("/submit")
    public Map<String, Object> submit(@Valid @RequestBody OfficeSubmitRequest request) {
        return Map.of("result", officeService.submit(request));
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of("stats", officeService.stats());
    }
}

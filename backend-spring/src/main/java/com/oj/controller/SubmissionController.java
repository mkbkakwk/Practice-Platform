package com.oj.controller;

import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import com.oj.dto.SubmitRequest;
import com.oj.dto.SubmissionView;
import com.oj.judge.LanguageDef;
import com.oj.service.SubmissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> submit(@Valid @RequestBody SubmitRequest req) {
        Integer userId = CurrentUser.getId();
        if (userId == null) throw ApiException.unauthorized("请先登录");
        if (!LanguageDef.isSupported(req.getLanguage())) {
            throw ApiException.badRequest("不支持的语言: " + req.getLanguage());
        }
        int id = submissionService.submit(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("submissionId", id, "status", "PENDING", "message", "已加入评测队列"));
    }

    @GetMapping("/meta/languages")
    public Map<String, Object> languages() {
        return Map.of("languages", LanguageDef.ALL);
    }

    @GetMapping("/{id}")
    public Map<String, Object> getById(@PathVariable int id) {
        SubmissionView sub = submissionService.getById(id);
        return Map.of("submission", sub);
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer problemId) {
        page = Math.max(1, page);
        pageSize = Math.min(50, Math.max(1, pageSize));
        List<SubmissionView> subs = submissionService.listFeed(page, pageSize, problemId);
        long total = submissionService.countFeed(problemId);
        return Map.of("total", total, "page", page, "pageSize", pageSize, "submissions", subs);
    }
}

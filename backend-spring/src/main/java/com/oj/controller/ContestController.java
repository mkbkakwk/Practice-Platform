package com.oj.controller;

import com.oj.dto.*;
import com.oj.judge.LanguageDef;
import com.oj.service.ContestService;
import com.oj.service.ContestStandingService;
import com.oj.service.ContestRejudgeService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/contests")
public class ContestController {
    private final ContestService service;
    private final ContestStandingService standings;
    private final ContestRejudgeService rejudge;

    public ContestController(ContestService service, ContestStandingService standings,
                             ContestRejudgeService rejudge) {
        this.service = service;
        this.standings = standings;
        this.rejudge = rejudge;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int pageSize) {
        return service.list(Math.max(1, page), Math.min(50, Math.max(1, pageSize)));
    }

    @GetMapping("/students")
    public Map<String, Object> students(@RequestParam(defaultValue = "") String query,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int pageSize) {
        return service.students(query, Math.max(1, page), Math.min(50, Math.max(1, pageSize)));
    }

    @GetMapping("/{contestId}")
    public Map<String, Object> detail(@PathVariable int contestId) {
        return Map.of("detail", service.detail(contestId));
    }

    @GetMapping("/{contestId}/standings")
    public Map<String, Object> standings(@PathVariable int contestId) {
        return Map.of("standings", standings.standings(contestId));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody ContestUpsertRequest request) {
        var contest = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("detail", service.detail(contest.getId())));
    }

    @PutMapping("/{contestId}")
    public Map<String, Object> update(@PathVariable int contestId,
                                      @Valid @RequestBody ContestUpsertRequest request) {
        service.update(contestId, request);
        return Map.of("detail", service.detail(contestId));
    }

    @PostMapping("/{contestId}/publish")
    public Map<String, Object> publish(@PathVariable int contestId) {
        service.publish(contestId);
        return Map.of("detail", service.detail(contestId));
    }

    @PostMapping("/{contestId}/cancel")
    public Map<String, Object> cancel(@PathVariable int contestId) {
        service.cancel(contestId);
        return Map.of("detail", service.detail(contestId));
    }

    @DeleteMapping("/{contestId}")
    public Map<String, Object> delete(@PathVariable int contestId) {
        return service.delete(contestId);
    }

    @PostMapping("/{contestId}/join")
    public Map<String, Object> join(@PathVariable int contestId) {
        return Map.of("participant", service.join(contestId));
    }

    @GetMapping("/{contestId}/participants")
    public Map<String, Object> participants(@PathVariable int contestId,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int pageSize) {
        return service.participants(contestId, Math.max(1, page), Math.min(50, Math.max(1, pageSize)));
    }

    @PostMapping("/{contestId}/participants")
    public ResponseEntity<Map<String, Object>> addParticipant(
            @PathVariable int contestId, @Valid @RequestBody ContestParticipantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("participant", service.addParticipant(contestId, request.getUserId())));
    }

    @DeleteMapping("/{contestId}/participants/{userId}")
    public Map<String, Object> removeParticipant(@PathVariable int contestId, @PathVariable int userId) {
        return service.removeParticipant(contestId, userId);
    }

    @PostMapping("/{contestId}/problems")
    public ResponseEntity<Map<String, Object>> addProblem(
            @PathVariable int contestId, @Valid @RequestBody ContestProblemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("contestProblem", service.addProblem(contestId, request)));
    }

    @PutMapping("/{contestId}/problems/order")
    public Map<String, Object> reorderProblems(
            @PathVariable int contestId, @Valid @RequestBody ContestProblemOrderRequest request) {
        return Map.of("problems", service.reorderProblems(contestId, request.getContestProblemIds()));
    }

    @DeleteMapping("/{contestId}/problems/{contestProblemId}")
    public Map<String, Object> removeProblem(@PathVariable int contestId,
                                             @PathVariable long contestProblemId) {
        return service.removeProblem(contestId, contestProblemId);
    }

    @PostMapping("/{contestId}/problems/{contestProblemId}/submissions")
    public ResponseEntity<Map<String, Object>> submitAlgorithm(
            @PathVariable int contestId,
            @PathVariable long contestProblemId,
            @Valid @RequestBody ContestAlgorithmSubmitRequest request) {
        if (!LanguageDef.isSupported(request.getLanguage())) {
            throw com.oj.common.ApiException.badRequest("不支持的语言: " + request.getLanguage());
        }
        int id = service.submitAlgorithm(contestId, contestProblemId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("submissionId", id, "status", "PENDING", "message", "已加入评测队列"));
    }

    @PostMapping("/{contestId}/problems/{contestProblemId}/office-submissions")
    public ResponseEntity<Map<String, Object>> submitOffice(
            @PathVariable int contestId,
            @PathVariable long contestProblemId,
            @RequestParam("file") MultipartFile file) {
        OfficeSubmissionDtos.StudentSubmission submission = service.submitOffice(contestId, contestProblemId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("submission", submission));
    }

    @PostMapping("/{contestId}/problems/{contestProblemId}/choice-submissions")
    public ResponseEntity<Map<String, Object>> submitChoice(
            @PathVariable int contestId,
            @PathVariable long contestProblemId,
            @Valid @RequestBody ContestChoiceSubmitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("submission", service.submitChoice(contestId, contestProblemId, request)));
    }

    @PostMapping("/{contestId}/rejudge/submissions/{submissionId}")
    public ResponseEntity<Map<String, Object>> rejudgeSubmission(@PathVariable int contestId,
                                                                  @PathVariable int submissionId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("batch", rejudge.rejudgeSubmission(contestId, submissionId)));
    }

    @PostMapping("/{contestId}/problems/{contestProblemId}/rejudge")
    public ResponseEntity<Map<String, Object>> rejudgeProblem(@PathVariable int contestId,
                                                               @PathVariable long contestProblemId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("batch", rejudge.rejudgeProblem(contestId, contestProblemId)));
    }

    @PostMapping("/{contestId}/rejudge")
    public ResponseEntity<Map<String, Object>> rejudgeContest(@PathVariable int contestId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("batch", rejudge.rejudgeContest(contestId)));
    }

    @GetMapping("/{contestId}/rejudge/batches/{batchId}")
    public Map<String, Object> rejudgeBatch(@PathVariable int contestId, @PathVariable long batchId) {
        return Map.of("batch", rejudge.batch(contestId, batchId));
    }

    @GetMapping("/{contestId}/rejudge/submissions")
    public Map<String, Object> rejudgeableSubmissions(@PathVariable int contestId,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int pageSize) {
        return rejudge.rejudgeableSubmissions(contestId, Math.max(1, page), Math.min(50, Math.max(1, pageSize)));
    }

    @GetMapping("/{contestId}/problems/{contestProblemId}/starter")
    public ResponseEntity<FileSystemResource> downloadStarter(
            @PathVariable int contestId, @PathVariable long contestProblemId) {
        ContestService.StarterDocument document = service.contestStarter(contestId, contestProblemId);
        FileSystemResource resource = new FileSystemResource(document.file());
        String filename = document.name() == null ? document.file().getName() : document.name();
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(resource);
    }
}

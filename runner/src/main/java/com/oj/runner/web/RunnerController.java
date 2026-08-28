package com.oj.runner.web;

import com.oj.runner.api.RunnerHealthResponse;
import com.oj.runner.api.RunnerJobRequest;
import com.oj.runner.api.RunnerJobResponse;
import com.oj.runner.service.RunnerJobService;
import com.oj.runner.service.BoundedReadinessProbe;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class RunnerController {

    private final RunnerJobService jobService;
    private final BoundedReadinessProbe readinessProbe;

    public RunnerController(RunnerJobService jobService, BoundedReadinessProbe readinessProbe) {
        this.jobService = jobService;
        this.readinessProbe = readinessProbe;
    }

    @GetMapping("/health")
    public RunnerHealthResponse health() {
        return new RunnerHealthResponse(true, jobService.sandboxAvailable());
    }

    @GetMapping("/liveness")
    public Map<String, String> liveness() {
        return Map.of("status", "UP");
    }

    @GetMapping("/readiness")
    public ResponseEntity<Map<String, String>> readiness() {
        boolean ready = readinessProbe.check(jobService::sandboxAvailable);
        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", ready ? "UP" : "DOWN"));
    }

    @PostMapping("/v1/jobs")
    public RunnerJobResponse execute(@RequestBody RunnerJobRequest request) {
        return jobService.execute(request);
    }
}

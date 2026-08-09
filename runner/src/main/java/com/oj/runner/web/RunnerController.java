package com.oj.runner.web;

import com.oj.runner.api.RunnerHealthResponse;
import com.oj.runner.api.RunnerJobRequest;
import com.oj.runner.api.RunnerJobResponse;
import com.oj.runner.service.RunnerJobService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RunnerController {

    private final RunnerJobService jobService;

    public RunnerController(RunnerJobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping("/health")
    public RunnerHealthResponse health() {
        return new RunnerHealthResponse(true, jobService.sandboxAvailable());
    }

    @PostMapping("/v1/jobs")
    public RunnerJobResponse execute(@RequestBody RunnerJobRequest request) {
        return jobService.execute(request);
    }
}

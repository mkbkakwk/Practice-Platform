package com.oj.runner.service;

import com.oj.runner.api.RunnerCaseResult;
import com.oj.runner.api.RunnerCompileResult;
import com.oj.runner.api.RunnerJobRequest;
import com.oj.runner.api.RunnerJobResponse;
import com.oj.runner.api.RunnerStatus;
import com.oj.runner.execution.RunnerResponses;
import com.oj.runner.execution.SandboxExecutor;
import com.oj.runner.language.RunnerJob;
import com.oj.runner.language.RunnerRequestValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RunnerJobService {

    private static final Logger log = LoggerFactory.getLogger(RunnerJobService.class);

    private final RunnerRequestValidator requestValidator;
    private final JobConcurrencyLimiter concurrencyLimiter;
    private final SandboxExecutor sandboxExecutor;

    public RunnerJobService(
            RunnerRequestValidator requestValidator,
            JobConcurrencyLimiter concurrencyLimiter,
            SandboxExecutor sandboxExecutor) {
        this.requestValidator = requestValidator;
        this.concurrencyLimiter = concurrencyLimiter;
        this.sandboxExecutor = sandboxExecutor;
    }

    public RunnerJobResponse execute(RunnerJobRequest request) {
        RunnerJob job = requestValidator.validate(request);
        if (!concurrencyLimiter.tryAcquire()) {
            throw new RunnerSaturatedException();
        }

        long startedAt = System.currentTimeMillis();
        RunnerJobResponse response;
        try {
            response = validateExecutorResponse(job, sandboxExecutor.execute(job));
        } catch (RuntimeException exception) {
            log.warn("Runner executor failed requestId={} type={}",
                    request.requestId(), exception.getClass().getSimpleName());
            response = RunnerResponses.systemError(request.requestId(), "Sandbox executor failed");
        } finally {
            concurrencyLimiter.release();
        }

        long durationMs = System.currentTimeMillis() - startedAt;
        log.info("Runner job requestId={} language={} caseCount={} status={} durationMs={}",
                request.requestId(), request.language(), request.cases().size(),
                finalStatus(response), durationMs);
        return response;
    }

    public boolean sandboxAvailable() {
        return sandboxExecutor.available();
    }

    private RunnerJobResponse validateExecutorResponse(RunnerJob job, RunnerJobResponse response) {
        if (response == null || !job.request().requestId().equals(response.requestId())
                || response.compile() == null || response.compile().status() == null
                || response.compile().exitCode() == null || response.compile().stderr() == null
                || response.compile().message() == null || response.compile().timeMs() < 0
                || response.cases() == null || response.message() == null) {
            throw new IllegalStateException("Sandbox executor returned an invalid result");
        }
        RunnerCompileResult compile = response.compile();
        long outputLimit = job.request().limits().outputLimitBytes();
        if (utf8Length(compile.stderr()) > outputLimit
                || utf8Length(compile.message()) > outputLimit
                || utf8Length(response.message()) > outputLimit) {
            throw new IllegalStateException("Sandbox executor exceeded the output limit");
        }
        if (compile.status() == RunnerStatus.OK && compile.exitCode() != 0) {
            throw new IllegalStateException("Sandbox executor returned an invalid compile result");
        }
        if (compile.status() != RunnerStatus.OK) {
            if (!response.cases().isEmpty()) {
                throw new IllegalStateException("Sandbox executor returned cases after compile failure");
            }
            return response;
        }
        if (response.cases().isEmpty() || response.cases().size() > job.request().cases().size()) {
            throw new IllegalStateException("Sandbox executor returned an invalid case count");
        }
        validateCases(job, response.cases());
        return response;
    }

    private void validateCases(RunnerJob job, List<RunnerCaseResult> results) {
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < results.size(); index++) {
            RunnerCaseResult result = results.get(index);
            String expectedId = job.request().cases().get(index).caseId();
            if (result == null || result.status() == null || !expectedId.equals(result.caseId())
                    || !seen.add(result.caseId()) || result.exitCode() == null
                    || result.stdout() == null || result.stderr() == null || result.message() == null
                    || result.timeMs() < 0 || result.memoryKb() < 0
                    || (result.status() == RunnerStatus.OK && result.exitCode() != 0)) {
                throw new IllegalStateException("Sandbox executor returned an invalid case result");
            }
            long outputBytes = utf8Length(result.stdout())
                    + utf8Length(result.stderr())
                    + utf8Length(result.message());
            if (outputBytes > job.request().limits().outputLimitBytes()) {
                throw new IllegalStateException("Sandbox executor exceeded the output limit");
            }
            if (index < results.size() - 1 && result.status() != RunnerStatus.OK) {
                throw new IllegalStateException("Sandbox executor returned results after a failed case");
            }
        }
        RunnerCaseResult last = results.getLast();
        if (results.size() < job.request().cases().size() && last.status() == RunnerStatus.OK) {
            throw new IllegalStateException("Sandbox executor omitted successful cases");
        }
    }

    private RunnerStatus finalStatus(RunnerJobResponse response) {
        return response.compile().status() != RunnerStatus.OK || response.cases().isEmpty()
                ? response.compile().status()
                : response.cases().getLast().status();
    }

    private long utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}

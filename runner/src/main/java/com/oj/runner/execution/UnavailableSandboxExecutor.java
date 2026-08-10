package com.oj.runner.execution;

import com.oj.runner.api.RunnerJobResponse;
import com.oj.runner.language.RunnerJob;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fail-closed Stage 3B-1 executor. It never starts student code.
 * Stage 3B-2 replaces this with the dedicated Linux isolation implementation.
 */
@Component
@Profile("!runner-contract-test")
public class UnavailableSandboxExecutor implements SandboxExecutor {

    @Override
    public RunnerJobResponse execute(RunnerJob job) {
        return RunnerResponses.systemError(
                job.request().requestId(), "Sandbox executor unavailable");
    }

    @Override
    public boolean available() {
        return false;
    }
}

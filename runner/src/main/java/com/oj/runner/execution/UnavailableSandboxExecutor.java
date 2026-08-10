package com.oj.runner.execution;

import com.oj.runner.api.RunnerJobResponse;
import com.oj.runner.language.RunnerJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fail-closed default executor. It never starts student code.
 * Linux isolation must be selected explicitly after dedicated-host acceptance.
 */
@Component
@Profile("!runner-contract-test")
@ConditionalOnProperty(prefix = "runner.sandbox", name = "mode", havingValue = "unavailable", matchIfMissing = true)
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

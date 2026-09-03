package com.oj.runner.execution;

import com.oj.runner.api.RunnerCaseResult;
import com.oj.runner.api.RunnerCompileResult;
import com.oj.runner.api.RunnerJobResponse;
import com.oj.runner.api.RunnerStatus;
import com.oj.runner.language.RunnerJob;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/** Disposable Docker contract-test executor. Never enable this profile in a deployment. */
@Component
@Profile("runner-contract-test")
public class ContractTestSandboxExecutor implements SandboxExecutor {

    @Override
    public RunnerJobResponse execute(RunnerJob job) {
        RunnerCompileResult compile = new RunnerCompileResult(RunnerStatus.OK, 0, "", 1, "");
        List<RunnerCaseResult> cases = job.request().cases().stream()
                .map(testCase -> new RunnerCaseResult(
                        testCase.caseId(), RunnerStatus.OK, 0, "contract-ok\n", "", 1, 0, ""))
                .toList();
        return new RunnerJobResponse(job.request().requestId(), compile, cases, "");
    }

    @Override
    public boolean available() {
        return false;
    }
}

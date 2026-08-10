package com.oj.runner.execution;

import com.oj.runner.api.RunnerJobResponse;
import com.oj.runner.language.RunnerJob;

public interface SandboxExecutor {

    RunnerJobResponse execute(RunnerJob job);

    boolean available();
}

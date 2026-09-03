package com.oj.runner.execution;

import com.oj.runner.api.RunnerCompileResult;
import com.oj.runner.api.RunnerJobResponse;
import com.oj.runner.api.RunnerStatus;

import java.util.List;

public final class RunnerResponses {

    private RunnerResponses() {
    }

    public static RunnerJobResponse systemError(String requestId, String message) {
        RunnerCompileResult compile = new RunnerCompileResult(
                RunnerStatus.SYSTEM_ERROR, -1, "", 0, message);
        return new RunnerJobResponse(requestId, compile, List.of(), message);
    }
}

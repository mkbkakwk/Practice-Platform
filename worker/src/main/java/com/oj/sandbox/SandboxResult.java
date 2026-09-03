package com.oj.sandbox;

import java.util.List;

public record SandboxResult(
        String requestId,
        SandboxCompileResult compile,
        List<SandboxCaseResult> cases,
        String message) {
}

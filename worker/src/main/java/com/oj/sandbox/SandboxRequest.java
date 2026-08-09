package com.oj.sandbox;

import java.util.List;

public record SandboxRequest(
        String requestId,
        SandboxLanguage language,
        String sourceCode,
        SandboxLimits limits,
        List<SandboxTestCase> cases) {
}

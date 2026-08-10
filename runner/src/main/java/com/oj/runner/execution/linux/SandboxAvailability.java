package com.oj.runner.execution.linux;

import java.util.List;

public record SandboxAvailability(boolean supported, List<String> reasons) {

    public SandboxAvailability {
        reasons = List.copyOf(reasons);
    }
}

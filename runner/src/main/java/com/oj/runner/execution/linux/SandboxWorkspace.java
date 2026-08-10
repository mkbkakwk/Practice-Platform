package com.oj.runner.execution.linux;

import java.nio.file.Path;

public record SandboxWorkspace(Path root, Path files, Path metadata) {
}

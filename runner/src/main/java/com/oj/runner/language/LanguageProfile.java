package com.oj.runner.language;

import com.oj.runner.api.RunnerLanguage;

import java.util.List;
import java.util.Map;

public record LanguageProfile(
        RunnerLanguage language,
        String sourceFilename,
        String compileProfile,
        String runtimeProfile,
        boolean compiled,
        List<String> compileArgv,
        List<String> runArgv,
        List<String> requiredRuntimePaths,
        Map<String, String> environment,
        RuntimeMemoryPolicy memoryPolicy) {

    public LanguageProfile {
        compileArgv = List.copyOf(compileArgv);
        runArgv = List.copyOf(runArgv);
        requiredRuntimePaths = List.copyOf(requiredRuntimePaths);
        environment = Map.copyOf(environment);
    }
}

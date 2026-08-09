package com.oj.runner.language;

import com.oj.runner.api.RunnerLanguage;

public record LanguageProfile(
        RunnerLanguage language,
        String sourceFilename,
        String compileProfile,
        String runtimeProfile,
        boolean compiled) {
}

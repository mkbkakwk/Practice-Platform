package com.oj.runner.language;

import com.oj.runner.api.RunnerJobRequest;

public record RunnerJob(RunnerJobRequest request, LanguageProfile profile) {
}

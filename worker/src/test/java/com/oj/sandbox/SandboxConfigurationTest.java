package com.oj.sandbox;

import com.oj.sandbox.local.LegacyLocalSandboxClient;
import com.oj.sandbox.remote.RemoteSandboxClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxConfigurationTest {

    @TempDir
    Path workspace;

    @Test
    void missingModeDefaultsToLegacyLocal() {
        context().run(context -> assertThat(context.getBean(SandboxClient.class))
                .isInstanceOf(LegacyLocalSandboxClient.class));
    }

    @Test
    void remoteModeRequiresBaseUrlAndTokenAtStartup() {
        context().withPropertyValues("oj.judge.execution-mode=remote")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("RUNNER_BASE_URL is required in remote execution mode");
                });
    }

    @Test
    void remoteModeCreatesOnlyRemoteClientWhenConfigured() {
        context().withPropertyValues(
                        "oj.judge.execution-mode=remote",
                        "oj.judge.runner.base-url=http://runner.internal:8080",
                        "oj.judge.runner.token=test-only-token")
                .run(context -> assertThat(context.getBean(SandboxClient.class))
                        .isInstanceOf(RemoteSandboxClient.class));
    }

    @Test
    void unknownModeFailsStartup() {
        context().withPropertyValues("oj.judge.execution-mode=fallback")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("Unsupported JUDGE_EXECUTION_MODE: fallback");
                });
    }

    private ApplicationContextRunner context() {
        return new ApplicationContextRunner()
                .withUserConfiguration(SandboxConfiguration.class)
                .withPropertyValues("oj.judge.workspace=" + workspace.toString().replace('\\', '/'));
    }
}

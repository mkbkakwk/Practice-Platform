package com.oj.runner;

import com.oj.runner.config.RunnerProperties;
import com.oj.runner.web.RunnerAuthenticationFilter;
import com.oj.runner.web.RunnerResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RunnerTokenConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TokenPropertiesConfiguration.class);

    @Test
    void missingTokenPreventsRunnerConfigurationFromStarting() {
        contextRunner.withPropertyValues("runner.token=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(ConfigurationPropertiesBindException.class);
                });
    }

    @Test
    void blankTokenPreventsAuthenticationBoundaryFromStarting() {
        RunnerProperties properties = new RunnerProperties();
        properties.setToken("  ");

        assertThatThrownBy(() -> new RunnerAuthenticationFilter(
                properties, mock(RunnerResponseWriter.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNNER_TOKEN");
    }

    @Test
    void tokenContainingLineBreaksIsRejected() {
        RunnerProperties properties = new RunnerProperties();
        properties.setToken("unsafe\ntoken");

        assertThatThrownBy(() -> new RunnerAuthenticationFilter(
                properties, mock(RunnerResponseWriter.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("line breaks");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RunnerProperties.class)
    static class TokenPropertiesConfiguration {
    }
}

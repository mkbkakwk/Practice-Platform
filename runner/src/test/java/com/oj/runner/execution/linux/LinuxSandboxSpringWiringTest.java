package com.oj.runner.execution.linux;

import com.oj.runner.config.LinuxSandboxProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LinuxSandboxSpringWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LinuxWiringConfiguration.class)
            .withPropertyValues("runner.sandbox.mode=linux");

    @Test
    void wiresExecutionCgroupManagerIntoLinuxSandboxExecutorInLinuxMode() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ExecutionCgroupManager.class);
            assertThat(context.getBean(ExecutionCgroupManager.class).getClass())
                    .isEqualTo(ExecutionCgroupManager.class);
            assertThat(context).hasSingleBean(LinuxSandboxExecutor.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LinuxSandboxProperties.class)
    @Import({ExecutionCgroupManager.class, LinuxSandboxExecutor.class})
    static class LinuxWiringConfiguration {

        @Bean
        LinuxSandboxPreflight linuxSandboxPreflight() {
            return mock(LinuxSandboxPreflight.class);
        }

        @Bean
        SandboxWorkspaceManager sandboxWorkspaceManager() {
            return mock(SandboxWorkspaceManager.class);
        }

        @Bean
        NsJailConfigWriter nsJailConfigWriter() {
            return mock(NsJailConfigWriter.class);
        }

        @Bean
        LanguageCommandResolver languageCommandResolver() {
            return mock(LanguageCommandResolver.class);
        }

        @Bean
        SandboxProcessLauncher sandboxProcessLauncher() {
            return mock(SandboxProcessLauncher.class);
        }
    }
}

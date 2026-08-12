package com.oj.runner.execution.docker;

import com.oj.runner.api.RunnerLanguage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DockerLanguageCommandsTest {

    @Test
    void commandsAreFixedStructuredArgvForEveryLanguage() {
        for (RunnerLanguage language : RunnerLanguage.values()) {
            List<String> compile = DockerLanguageCommands.compile(language, 256);
            List<String> run = DockerLanguageCommands.run(language, 256);
            assertThat(compile).isNotEmpty().allMatch(value -> value != null && !value.isBlank());
            assertThat(run).isNotEmpty().allMatch(value -> value != null && !value.isBlank());
            assertThat(compile).noneMatch(value -> value.equals("sh") || value.equals("bash")
                    || value.equals("-c") || value.contains("${"));
            assertThat(run).noneMatch(value -> value.equals("sh") || value.equals("bash")
                    || value.equals("-c") || value.contains("${"));
        }
    }

    @Test
    void javaHeapIsBoundedBelowTheOuterContainerMemoryLimit() {
        assertThat(DockerLanguageCommands.run(RunnerLanguage.JAVA, 256))
                .contains("-Xmx153m", "-XX:ActiveProcessorCount=1");
        assertThat(DockerLanguageCommands.compile(RunnerLanguage.JAVA, 256))
                .contains("-J-Xmx153m");
    }
}

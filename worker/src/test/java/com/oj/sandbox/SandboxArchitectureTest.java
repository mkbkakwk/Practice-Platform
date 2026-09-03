package com.oj.sandbox;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxArchitectureTest {

    private static final Path MAIN_SOURCE = Path.of("src/main/java");

    @Test
    void judgeServiceContainsNoProcessOrLanguageCommandDetails() throws Exception {
        String source = Files.readString(MAIN_SOURCE.resolve("com/oj/judge/JudgeService.java"));

        assertThat(source).doesNotContain(
                "ProcessBuilder", "bash -c", "gcc", "g++", "javac", "python3", "node");
    }

    @Test
    void processBuilderIsConcentratedInTheExplicitLegacyAdapter() throws Exception {
        List<String> occurrences;
        try (var files = Files.walk(MAIN_SOURCE)) {
            occurrences = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains("new ProcessBuilder");
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .map(path -> MAIN_SOURCE.relativize(path).toString().replace('\\', '/'))
                    .toList();
        }

        assertThat(occurrences)
                .containsExactly("com/oj/sandbox/local/LegacyProcessRunner.java");
    }

    @Test
    void protocolDoesNotAcceptArbitraryExecutionCommands() {
        assertThat(SandboxRequest.class.getRecordComponents())
                .extracting(component -> component.getName().toLowerCase())
                .doesNotContain("command", "compilecommand", "runcommand", "shell", "args");
    }
}

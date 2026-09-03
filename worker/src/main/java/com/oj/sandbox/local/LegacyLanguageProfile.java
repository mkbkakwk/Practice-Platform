package com.oj.sandbox.local;

import com.oj.sandbox.SandboxLanguage;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

enum LegacyLanguageProfile {
    PYTHON(SandboxLanguage.PYTHON, "py", null, List.of("python3", "{src}")),
    JAVASCRIPT(SandboxLanguage.JAVASCRIPT, "js", null, List.of("node", "{src}")),
    C(SandboxLanguage.C, "c",
            List.of("gcc", "-std=c11", "-O2", "-o", "{out}", "{src}", "-lm"),
            List.of("{out}")),
    CPP17(SandboxLanguage.CPP17, "cpp",
            List.of("g++", "-std=c++17", "-O2", "-o", "{out}", "{src}"),
            List.of("{out}")),
    JAVA(SandboxLanguage.JAVA, "java", List.of("javac", "{src}"), List.of("java", "Main"));

    private final SandboxLanguage language;
    private final String extension;
    private final List<String> compileCommand;
    private final List<String> runCommand;

    LegacyLanguageProfile(
            SandboxLanguage language,
            String extension,
            List<String> compileCommand,
            List<String> runCommand) {
        this.language = language;
        this.extension = extension;
        this.compileCommand = compileCommand;
        this.runCommand = runCommand;
    }

    static LegacyLanguageProfile of(SandboxLanguage language) {
        return Arrays.stream(values())
                .filter(profile -> profile.language == language)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported sandbox language"));
    }

    String extension() {
        return extension;
    }

    List<String> compileCommand(Path source, Path output) {
        return resolve(compileCommand, source, output);
    }

    List<String> runCommand(Path source, Path output) {
        return resolve(runCommand, source, output);
    }

    private List<String> resolve(List<String> command, Path source, Path output) {
        if (command == null) {
            return null;
        }
        return command.stream()
                .map(argument -> argument
                        .replace("{src}", source.toString())
                        .replace("{out}", output.toString()))
                .toList();
    }
}

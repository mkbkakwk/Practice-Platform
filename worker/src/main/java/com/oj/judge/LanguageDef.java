package com.oj.judge;

import java.util.List;

/**
 * Supported languages. Mirrors the backend's list. Commands are argument lists
 * so paths and source code metadata are never interpreted as shell syntax.
 */
public record LanguageDef(
        String id,
        String ext,
        List<String> compileCommand,
        List<String> runCommand,
        String template) {

    public static final List<LanguageDef> ALL = List.of(
        new LanguageDef("python", "py",
            null,
            List.of("python3", "{src}"),
            ""),
        new LanguageDef("javascript", "js",
            null,
            List.of("node", "{src}"),
            ""),
        new LanguageDef("cpp", "cpp",
            List.of("g++", "-std=c++17", "-O2", "-o", "{out}", "{src}"),
            List.of("{out}"),
            ""),
        new LanguageDef("c", "c",
            List.of("gcc", "-std=c11", "-O2", "-o", "{out}", "{src}", "-lm"),
            List.of("{out}"),
            ""),
        new LanguageDef("java", "java",
            List.of("javac", "{src}"),
            List.of("java", "Main"),
            "")
    );

    public static LanguageDef of(String id) {
        return ALL.stream().filter(l -> l.id().equals(id)).findFirst().orElse(null);
    }
}

package com.oj.judge;

import java.util.List;

/**
 * Supported languages. Mirrors the backend's list. Each entry knows how to
 * produce the compile and run shell commands (run inside bash so ulimit works).
 */
public record LanguageDef(String id, String ext, String compileCmd, String runCmd, String template) {

    public static final List<LanguageDef> ALL = List.of(
        new LanguageDef("python", "py",
            null,
            "python3 {src}",
            ""),
        new LanguageDef("javascript", "js",
            null,
            "node {src}",
            ""),
        new LanguageDef("cpp", "cpp",
            "g++ -std=c++17 -O2 -o {out} {src}",
            "{out}",
            ""),
        new LanguageDef("c", "c",
            "gcc -std=c11 -O2 -o {out} {src} -lm",
            "{out}",
            ""),
        new LanguageDef("java", "java",
            "javac {src}",
            "cd {dir} && java Main",
            "")
    );

    public static LanguageDef of(String id) {
        return ALL.stream().filter(l -> l.id().equals(id)).findFirst().orElse(null);
    }
}

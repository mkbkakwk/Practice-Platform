package com.oj.runner.language;

import com.oj.runner.api.RunnerLanguage;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class LanguageProfileRegistry {

    private final Map<RunnerLanguage, LanguageProfile> profiles;

    public LanguageProfileRegistry() {
        Map<String, String> baseEnvironment = Map.of(
                "PATH", "/usr/bin:/bin",
                "LANG", "C.UTF-8",
                "LC_ALL", "C.UTF-8",
                "HOME", "/workspace");
        EnumMap<RunnerLanguage, LanguageProfile> configured = new EnumMap<>(RunnerLanguage.class);
        configured.put(RunnerLanguage.PYTHON,
                new LanguageProfile(RunnerLanguage.PYTHON, "Main.py", "python-3-syntax", "python-3", true,
                        List.of("/usr/bin/python3", "-I", "-m", "py_compile", "/workspace/Main.py"),
                        List.of("/usr/bin/python3", "-I", "-B", "/workspace/Main.py"),
                        List.of("/usr/bin/python3"), baseEnvironment, RuntimeMemoryPolicy.NONE));
        configured.put(RunnerLanguage.JAVASCRIPT,
                new LanguageProfile(RunnerLanguage.JAVASCRIPT, "Main.js", "node-22-syntax", "node-22", true,
                        List.of("/usr/bin/node", "--check", "/workspace/Main.js"),
                        List.of("/usr/bin/node", "/workspace/Main.js"),
                        List.of("/usr/bin/node"), baseEnvironment, RuntimeMemoryPolicy.NODE));
        configured.put(RunnerLanguage.C,
                new LanguageProfile(RunnerLanguage.C, "Main.c", "gnu-c17", "native", true,
                        List.of("/usr/bin/gcc", "-std=c17", "-O2", "-pipe", "-o", "/workspace/program",
                                "/workspace/Main.c"),
                        List.of("/workspace/program"), List.of("/usr/bin/gcc"),
                        baseEnvironment, RuntimeMemoryPolicy.NONE));
        configured.put(RunnerLanguage.CPP17,
                new LanguageProfile(RunnerLanguage.CPP17, "Main.cpp", "gnu-cpp17", "native", true,
                        List.of("/usr/bin/g++", "-std=c++17", "-O2", "-pipe", "-o", "/workspace/program",
                                "/workspace/Main.cpp"),
                        List.of("/workspace/program"), List.of("/usr/bin/g++"),
                        baseEnvironment, RuntimeMemoryPolicy.NONE));
        Map<String, String> javaEnvironment = new HashMap<>(baseEnvironment);
        javaEnvironment.put("JAVA_HOME", "/usr/lib/jvm/java-21-openjdk-amd64");
        configured.put(RunnerLanguage.JAVA,
                new LanguageProfile(RunnerLanguage.JAVA, "Main.java", "java-21", "java-21", true,
                        List.of("/usr/bin/javac", "-encoding", "UTF-8", "-d", "/workspace/classes",
                                "/workspace/Main.java"),
                        List.of("/usr/bin/java", "-Djava.io.tmpdir=/tmp", "-XX:ActiveProcessorCount=1",
                                "-cp", "/workspace/classes", "Main"),
                        List.of("/usr/bin/javac", "/usr/bin/java", "/usr/lib/jvm/java-21-openjdk-amd64"),
                        javaEnvironment, RuntimeMemoryPolicy.JAVA));
        profiles = Map.copyOf(configured);
    }

    public LanguageProfile require(RunnerLanguage language) {
        LanguageProfile profile = language == null ? null : profiles.get(language);
        if (profile == null) {
            throw new RunnerRequestValidationException("Runner language is unsupported");
        }
        return profile;
    }

    public Map<RunnerLanguage, LanguageProfile> profiles() {
        return profiles;
    }
}

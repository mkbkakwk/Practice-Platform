package com.oj.runner.execution.docker;

import com.oj.runner.api.RunnerLanguage;

import java.util.List;

final class DockerLanguageCommands {

    private DockerLanguageCommands() {
    }

    static List<String> compile(RunnerLanguage language, long memoryMb) {
        return switch (language) {
            case PYTHON -> List.of("/usr/local/bin/python3", "-m", "py_compile", "/workspace/Main.py");
            case JAVASCRIPT -> List.of("/usr/local/bin/node", "--check", "/workspace/Main.js");
            case C -> List.of("/usr/local/bin/gcc", "-std=c17", "-O2", "-pipe",
                    "-o", "/workspace/Main", "/workspace/Main.c");
            case CPP17 -> List.of("/usr/local/bin/g++", "-std=c++17", "-O2", "-pipe",
                    "-o", "/workspace/Main", "/workspace/Main.cpp");
            case JAVA -> List.of("/opt/java/openjdk/bin/javac", javaHeapFlag("-J-Xmx", memoryMb),
                    "-encoding", "UTF-8", "-d", "/workspace", "/workspace/Main.java");
        };
    }

    static List<String> run(RunnerLanguage language, long memoryMb) {
        List<String> languageCommand = switch (language) {
            case PYTHON -> List.of("/usr/local/bin/python3", "-B", "/artifacts/Main.py");
            case JAVASCRIPT -> List.of("/usr/local/bin/node", "/artifacts/Main.js");
            case C, CPP17 -> List.of("/artifacts/Main");
            case JAVA -> List.of("/opt/java/openjdk/bin/java", javaHeapFlag("-Xmx", memoryMb),
                    "-XX:ActiveProcessorCount=1", "-cp", "/artifacts", "Main");
        };
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add("/usr/local/bin/sandbox-exec");
        command.add("/input/stdin");
        command.addAll(languageCommand);
        return List.copyOf(command);
    }

    private static String javaHeapFlag(String prefix, long memoryMb) {
        long heapMb = Math.max(16, Math.min(memoryMb - 16, memoryMb * 3 / 5));
        return prefix + heapMb + "m";
    }
}

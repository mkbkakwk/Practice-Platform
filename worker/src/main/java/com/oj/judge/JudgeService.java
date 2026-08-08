package com.oj.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Core judging logic: compile once, then run each test case, comparing
 * normalized output. Returns a JudgeResult describing the verdict.
 *
 * Mirrors the Node judge.service.ts semantics exactly so behaviour stays
 * consistent across the migration.
 */
public class JudgeService {

    private final Runner runner = new Runner();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path workspace;

    public JudgeService(Path workspace) throws IOException {
        this.workspace = workspace;
        Files.createDirectories(workspace);
    }

    public static class TestCase {
        public String input;
        public String output;
    }

    public static class JudgeResult {
        public String verdict;
        public int passed;
        public int total;
        public long timeMs;
        public long memoryKb;
        public String message;
        public Integer failedCase;
        public String failedInput;
        public String failedExpected;
        public String failedActual;
    }

    public JudgeResult judge(String language, String code, long timeLimitMs, long memoryLimitKb, String testCasesJson) {
        LanguageDef lang = LanguageDef.of(language);
        JudgeResult r = new JudgeResult();
        if (lang == null) {
            r.verdict = "CE";
            r.message = "不支持的语言: " + language;
            try {
                TestCase[] tcs = mapper.readValue(testCasesJson, TestCase[].class);
                r.total = tcs.length;
            } catch (Exception ignored) {}
            return r;
        }

        TestCase[] testCases;
        try {
            testCases = mapper.readValue(testCasesJson, TestCase[].class);
        } catch (Exception e) {
            r.verdict = "SE";
            r.message = "测试点数据损坏: " + e.getMessage();
            return r;
        }
        if (testCases == null || testCases.length == 0) {
            r.verdict = "SE";
            r.total = 0;
            r.message = "No test cases configured";
            return r;
        }
        r.total = testCases.length;

        String runId = UUID.randomUUID().toString();
        Path dir = workspace.resolve(runId);
        try {
            Files.createDirectories(dir);
            Path srcPath = dir.resolve("Main." + lang.ext());
            Files.writeString(srcPath, code);
            Path outPath = dir.resolve("main_out");

            // JVM and V8 reserve large virtual address spaces; ulimit -v would prevent
            // either runtime from starting before submitted code executes.
            long memCap = ("java".equals(language) || "javascript".equals(language)) ? 0 : memoryLimitKb * 2;

            // 1) Compile (if needed)
            if (lang.compileCommand() != null) {
                List<String> cmd = resolveCommand(lang.compileCommand(), srcPath, outPath);
                RunResult cr = runner.run(cmd, "", 10000, 0, dir);
                if (cr.timedOut || cr.exitCode != 0) {
                    r.verdict = "CE";
                    r.message = (cr.stderr == null || cr.stderr.isBlank()) ? "编译失败" : cr.stderr.trim();
                    cleanup(dir);
                    return r;
                }
            }

            // 2) Run each test case
            int passed = 0;
            long maxTime = 0;
            for (int i = 0; i < testCases.length; i++) {
                TestCase tc = testCases[i];
                List<String> runCmd = resolveCommand(lang.runCommand(), srcPath, outPath);
                RunResult rr = runner.run(runCmd, tc.input == null ? "" : tc.input, timeLimitMs, memCap, dir);

                if (rr.timedOut) {
                    r.verdict = "TLE";
                    r.passed = passed;
                    r.timeMs = Math.max(maxTime, timeLimitMs);
                    r.message = "第 " + (i + 1) + " 个测试点运行超时";
                    r.failedCase = i + 1;
                    cleanup(dir);
                    return r;
                }
                if (rr.memoryError) {
                    r.verdict = "RE";
                    r.passed = passed;
                    r.timeMs = maxTime;
                    r.memoryKb = memoryLimitKb;
                    r.message = "第 " + (i + 1) + " 个测试点内存超限";
                    r.failedCase = i + 1;
                    cleanup(dir);
                    return r;
                }
                if (rr.exitCode != 0) {
                    r.verdict = "RE";
                    r.passed = passed;
                    r.timeMs = maxTime;
                    r.message = "第 " + (i + 1) + " 个测试点运行错误 (退出码 " + rr.exitCode + ")";
                    r.failedCase = i + 1;
                    cleanup(dir);
                    return r;
                }
                maxTime = Math.max(maxTime, rr.elapsedMs);
                if (!normalize(tc.output).equals(normalize(rr.stdout))) {
                    r.verdict = "WA";
                    r.passed = passed;
                    r.timeMs = maxTime;
                    r.message = "第 " + (i + 1) + " 个测试点答案错误";
                    r.failedCase = i + 1;
                    r.failedInput = tc.input;
                    r.failedExpected = tc.output;
                    r.failedActual = rr.stdout;
                    cleanup(dir);
                    return r;
                }
                passed++;
            }
            r.verdict = "AC";
            r.passed = passed;
            r.timeMs = maxTime;
            r.message = "通过全部 " + testCases.length + " 个测试点";
            return r;
        } catch (Exception e) {
            r.verdict = "SE";
            r.message = "评测器异常: " + e.getMessage();
            return r;
        } finally {
            cleanup(dir);
        }
    }

    /** Trim trailing spaces per line, drop trailing blank lines, trim edges. */
    private String normalize(String s) {
        if (s == null) return "";
        String[] lines = s.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(lines[i].replaceAll("\\s+$", ""));
            if (i < lines.length - 1) sb.append("\n");
        }
        return sb.toString().replaceAll("\n+$", "").strip();
    }

    private List<String> resolveCommand(List<String> command, Path srcPath, Path outPath) {
        return command.stream()
                .map(argument -> argument
                        .replace("{src}", srcPath.toString())
                        .replace("{out}", outPath.toString()))
                .toList();
    }

    private void cleanup(Path dir) {
        try {
            if (Files.exists(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> {
                                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                            });
                }
            }
        } catch (IOException ignored) {}
    }
}

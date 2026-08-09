package com.oj.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.sandbox.SandboxCaseResult;
import com.oj.sandbox.SandboxClient;
import com.oj.sandbox.SandboxClientException;
import com.oj.sandbox.SandboxCompileResult;
import com.oj.sandbox.SandboxLanguage;
import com.oj.sandbox.SandboxLimits;
import com.oj.sandbox.SandboxRequest;
import com.oj.sandbox.SandboxResult;
import com.oj.sandbox.SandboxStatus;
import com.oj.sandbox.SandboxTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Business-level judging: parse cases, invoke an execution boundary, and decide verdicts. */
public class JudgeService {

    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);

    private final SandboxClient sandboxClient;
    private final long compileTimeoutMs;
    private final int outputLimitBytes;
    private final ObjectMapper mapper = new ObjectMapper();

    public JudgeService(SandboxClient sandboxClient, long compileTimeoutMs, int outputLimitBytes) {
        this.sandboxClient = sandboxClient;
        this.compileTimeoutMs = compileTimeoutMs;
        this.outputLimitBytes = outputLimitBytes;
    }

    public static class TestCase {
        public String input;
        public String output;
    }

    public static class JudgeResult {
        public String requestId;
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

    public JudgeResult judge(
            String language,
            String code,
            long timeLimitMs,
            long memoryLimitKb,
            String testCasesJson) {
        JudgeResult result = new JudgeResult();
        TestCase[] testCases = parseTestCases(testCasesJson, result);
        if (testCases == null) {
            return result;
        }
        result.total = testCases.length;

        SandboxLanguage sandboxLanguage = SandboxLanguage.fromPlatformId(language).orElse(null);
        if (sandboxLanguage == null) {
            result.verdict = "CE";
            result.message = "不支持的语言: " + language;
            return result;
        }

        String requestId = UUID.randomUUID().toString();
        result.requestId = requestId;
        SandboxRequest request = new SandboxRequest(
                requestId,
                sandboxLanguage,
                code,
                new SandboxLimits(
                        compileTimeoutMs,
                        timeLimitMs,
                        memoryLimitMb(memoryLimitKb),
                        outputLimitBytes),
                sandboxCases(testCases));

        long startedAt = System.nanoTime();
        try {
            SandboxResult sandboxResult = sandboxClient.execute(request);
            if (sandboxResult == null || !requestId.equals(sandboxResult.requestId())) {
                return systemError(result, "Runner response requestId mismatch");
            }
            JudgeResult mapped = mapResult(result, testCases, sandboxResult);
            log.info("[worker] sandbox requestId={} language={} status={} durationMs={} cases={}",
                    requestId, sandboxLanguage, mapped.verdict,
                    (System.nanoTime() - startedAt) / 1_000_000, testCases.length);
            return mapped;
        } catch (SandboxClientException exception) {
            log.warn("[worker] sandbox requestId={} language={} failed: {}",
                    requestId, sandboxLanguage, exception.getMessage());
            return systemError(result, "Runner unavailable or returned an invalid response");
        } catch (RuntimeException exception) {
            log.error("[worker] sandbox requestId={} language={} unexpected failure",
                    requestId, sandboxLanguage, exception);
            return systemError(result, "评测器异常");
        }
    }

    private TestCase[] parseTestCases(String testCasesJson, JudgeResult result) {
        TestCase[] testCases;
        try {
            testCases = mapper.readValue(testCasesJson, TestCase[].class);
        } catch (Exception exception) {
            result.verdict = "SE";
            result.message = "测试点数据损坏";
            return null;
        }
        if (testCases == null || testCases.length == 0) {
            result.verdict = "SE";
            result.total = 0;
            result.message = "No test cases configured";
            return null;
        }
        return testCases;
    }

    private List<SandboxTestCase> sandboxCases(TestCase[] testCases) {
        List<SandboxTestCase> cases = new ArrayList<>(testCases.length);
        for (int index = 0; index < testCases.length; index++) {
            String stdin = testCases[index].input == null ? "" : testCases[index].input;
            cases.add(new SandboxTestCase(Integer.toString(index + 1), stdin));
        }
        return List.copyOf(cases);
    }

    private JudgeResult mapResult(
            JudgeResult result,
            TestCase[] expectedCases,
            SandboxResult sandboxResult) {
        SandboxCompileResult compile = sandboxResult.compile();
        if (compile == null || compile.status() == null) {
            return systemError(result, "Runner returned an invalid compile result");
        }
        if (compile.status() != SandboxStatus.OK) {
            return mapCompileFailure(result, compile);
        }
        if (sandboxResult.cases() == null || sandboxResult.cases().isEmpty()
                || sandboxResult.cases().size() > expectedCases.length) {
            return systemError(result, "Runner returned an invalid case count");
        }

        int passed = 0;
        long maxTimeMs = 0;
        long maxMemoryKb = 0;
        for (int index = 0; index < sandboxResult.cases().size(); index++) {
            SandboxCaseResult actual = sandboxResult.cases().get(index);
            String expectedCaseId = Integer.toString(index + 1);
            if (actual == null || actual.status() == null || !expectedCaseId.equals(actual.caseId())) {
                return systemError(result, "Runner returned an invalid case result");
            }
            maxTimeMs = Math.max(maxTimeMs, actual.timeMs());
            maxMemoryKb = Math.max(maxMemoryKb, actual.memoryKb());
            if (actual.status() != SandboxStatus.OK) {
                result.passed = passed;
                result.timeMs = maxTimeMs;
                result.memoryKb = maxMemoryKb;
                result.failedCase = index + 1;
                return mapCaseFailure(result, actual, index + 1);
            }

            TestCase expected = expectedCases[index];
            if (!normalize(expected.output).equals(normalize(actual.stdout()))) {
                result.verdict = "WA";
                result.passed = passed;
                result.timeMs = maxTimeMs;
                result.memoryKb = maxMemoryKb;
                result.failedCase = index + 1;
                result.failedInput = expected.input;
                result.failedExpected = expected.output;
                result.failedActual = actual.stdout();
                result.message = "第 " + (index + 1) + " 个测试点答案错误";
                return result;
            }
            passed++;
        }

        if (passed != expectedCases.length) {
            return systemError(result, "Runner omitted test case results");
        }
        result.verdict = "AC";
        result.passed = passed;
        result.timeMs = maxTimeMs;
        result.memoryKb = maxMemoryKb;
        result.message = "通过全部 " + expectedCases.length + " 个测试点";
        return result;
    }

    private JudgeResult mapCompileFailure(JudgeResult result, SandboxCompileResult compile) {
        return switch (compile.status()) {
            case COMPILE_ERROR, TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED, OUTPUT_LIMIT_EXCEEDED -> {
                result.verdict = "CE";
                result.timeMs = compile.timeMs();
                result.message = nonBlank(compile.message(), nonBlank(compile.stderr(), "编译失败"));
                yield result;
            }
            case RUNTIME_ERROR, SYSTEM_ERROR -> systemError(result, "Runner compilation failed");
            case OK -> result;
        };
    }

    private JudgeResult mapCaseFailure(JudgeResult result, SandboxCaseResult actual, int caseNumber) {
        switch (actual.status()) {
            case TIME_LIMIT_EXCEEDED -> {
                result.verdict = "TLE";
                result.message = "第 " + caseNumber + " 个测试点运行超时";
            }
            case RUNTIME_ERROR -> {
                result.verdict = "RE";
                result.message = "第 " + caseNumber + " 个测试点运行错误 (退出码 "
                        + nonNullExitCode(actual.exitCode()) + ")";
            }
            case MEMORY_LIMIT_EXCEEDED -> {
                result.verdict = "RE";
                result.message = "第 " + caseNumber + " 个测试点内存超限";
            }
            case OUTPUT_LIMIT_EXCEEDED -> {
                result.verdict = "RE";
                result.message = "第 " + caseNumber + " 个测试点输出超限";
            }
            case COMPILE_ERROR, SYSTEM_ERROR -> systemError(result, "Runner execution failed");
            case OK -> {
                return systemError(result, "Runner returned an invalid case status");
            }
        }
        return result;
    }

    private JudgeResult systemError(JudgeResult result, String message) {
        result.verdict = "SE";
        result.message = message;
        return result;
    }

    private int memoryLimitMb(long memoryLimitKb) {
        long value = Math.max(1, (memoryLimitKb + 1_023) / 1_024);
        return (int) Math.min(Integer.MAX_VALUE, value);
    }

    private int nonNullExitCode(Integer exitCode) {
        return exitCode == null ? -1 : exitCode;
    }

    private String nonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred.trim();
    }

    /** Trim trailing spaces per line, drop trailing blank lines, trim edges. */
    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String[] lines = value.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
        StringBuilder normalized = new StringBuilder();
        for (int index = 0; index < lines.length; index++) {
            normalized.append(lines[index].replaceAll("\\s+$", ""));
            if (index < lines.length - 1) {
                normalized.append("\n");
            }
        }
        return normalized.toString().replaceAll("\n+$", "").strip();
    }
}

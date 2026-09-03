package com.oj.judge;

import com.oj.sandbox.SandboxCaseResult;
import com.oj.sandbox.SandboxClient;
import com.oj.sandbox.SandboxClientException;
import com.oj.sandbox.SandboxCompileResult;
import com.oj.sandbox.SandboxRequest;
import com.oj.sandbox.SandboxResult;
import com.oj.sandbox.SandboxStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JudgeServiceProtocolTest {

    @Test
    void runnerOkIsComparedByWorkerAndBecomesAc() {
        AtomicReference<SandboxRequest> captured = new AtomicReference<>();
        JudgeService service = service(request -> {
            captured.set(request);
            return result(request, SandboxStatus.OK, 0, "42\n");
        });

        JudgeService.JudgeResult result = service.judge(
                "python", "print(42)", 1000, 262144,
                "[{\"input\":\"\",\"output\":\"42\"}]");

        assertThat(result.verdict).isEqualTo("AC");
        assertThat(result.requestId).isEqualTo(captured.get().requestId());
        assertThat(captured.get().language().name()).isEqualTo("PYTHON");
        assertThat(captured.get().cases().getFirst().stdin()).isEmpty();
    }

    @Test
    void runnerOkWithWrongOutputBecomesWa() {
        JudgeService service = service(request -> result(request, SandboxStatus.OK, 0, "wrong"));

        JudgeService.JudgeResult result = service.judge(
                "python", "print('wrong')", 1000, 262144,
                "[{\"input\":\"\",\"output\":\"expected\"}]");

        assertThat(result.verdict).isEqualTo("WA");
    }

    @Test
    void sendsAllCasesInOneCompileOnceSandboxRequest() {
        AtomicReference<SandboxRequest> captured = new AtomicReference<>();
        AtomicReference<Integer> calls = new AtomicReference<>(0);
        JudgeService service = service(request -> {
            captured.set(request);
            calls.set(calls.get() + 1);
            return new SandboxResult(
                    request.requestId(), okCompile(),
                    List.of(
                            new SandboxCaseResult("1", SandboxStatus.OK, 0, "1", "", 1, 1, ""),
                            new SandboxCaseResult("2", SandboxStatus.OK, 0, "2", "", 1, 1, "")),
                    "");
        });

        JudgeService.JudgeResult result = service.judge(
                "python", "print(input())", 1000, 262144,
                "[{\"input\":\"1\",\"output\":\"1\"},{\"input\":\"2\",\"output\":\"2\"}]");

        assertThat(result.verdict).isEqualTo("AC");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(captured.get().cases()).extracting(testCase -> testCase.caseId())
                .containsExactly("1", "2");
    }

    @Test
    void compileErrorBecomesCe() {
        JudgeService service = service(request -> new SandboxResult(
                request.requestId(),
                new SandboxCompileResult(SandboxStatus.COMPILE_ERROR, 1, "syntax error", 8, "syntax error"),
                List.of(), ""));

        JudgeService.JudgeResult result = judge(service);

        assertThat(result.verdict).isEqualTo("CE");
        assertThat(result.message).contains("syntax error");
    }

    @Test
    void runtimeErrorBecomesRe() {
        JudgeService service = service(request -> result(request, SandboxStatus.RUNTIME_ERROR, 7, ""));

        JudgeService.JudgeResult result = judge(service);

        assertThat(result.verdict).isEqualTo("RE");
        assertThat(result.message).contains("退出码 7");
    }

    @Test
    void timeoutBecomesTle() {
        JudgeService service = service(request -> result(
                request, SandboxStatus.TIME_LIMIT_EXCEEDED, -1, ""));

        assertThat(judge(service).verdict).isEqualTo("TLE");
    }

    @Test
    void runnerFailureBecomesSeWithoutASecondExecutionAttempt() {
        AtomicReference<Integer> calls = new AtomicReference<>(0);
        JudgeService service = service(request -> {
            calls.set(calls.get() + 1);
            throw new SandboxClientException("connection refused");
        });

        JudgeService.JudgeResult result = judge(service);

        assertThat(result.verdict).isEqualTo("SE");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void mismatchedRequestIdBecomesSe() {
        JudgeService service = service(request -> new SandboxResult(
                "00000000-0000-0000-0000-000000000000",
                okCompile(), List.of(), ""));

        assertThat(judge(service).verdict).isEqualTo("SE");
    }

    @Test
    void omittedSuccessfulCasesBecomeSe() {
        JudgeService service = service(request -> new SandboxResult(
                request.requestId(), okCompile(),
                List.of(new SandboxCaseResult("1", SandboxStatus.OK, 0, "1", "", 1, 1, "")), ""));

        JudgeService.JudgeResult result = service.judge(
                "python", "print(1)", 1000, 262144,
                "[{\"input\":\"\",\"output\":\"1\"},{\"input\":\"\",\"output\":\"1\"}]");

        assertThat(result.verdict).isEqualTo("SE");
    }

    private JudgeService service(SandboxClient client) {
        return new JudgeService(client, 10_000, 65_536);
    }

    private JudgeService.JudgeResult judge(JudgeService service) {
        return service.judge(
                "python", "print(1)", 1000, 262144,
                "[{\"input\":\"\",\"output\":\"1\"}]");
    }

    private SandboxResult result(
            SandboxRequest request,
            SandboxStatus status,
            int exitCode,
            String stdout) {
        return new SandboxResult(
                request.requestId(), okCompile(),
                List.of(new SandboxCaseResult(
                        "1", status, exitCode, stdout, "", 12, 1024, status.name())), "");
    }

    private SandboxCompileResult okCompile() {
        return new SandboxCompileResult(SandboxStatus.OK, 0, "", 3, "");
    }
}

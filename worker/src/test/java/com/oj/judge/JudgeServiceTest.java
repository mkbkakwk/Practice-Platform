package com.oj.judge;

import com.oj.sandbox.SandboxLanguage;
import com.oj.sandbox.local.LegacyLocalSandboxClient;
import com.oj.sandbox.local.LegacyProcessResult;
import com.oj.sandbox.local.LegacyProcessRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JudgeServiceTest {

    @TempDir
    Path tempDir;

    private JudgeService judgeService;

    @BeforeEach
    void createJudgeService() {
        judgeService = new JudgeService(
                new LegacyLocalSandboxClient(tempDir, 1_048_576, 1_048_576),
                10_000,
                16 * 1_024 * 1_024);
    }

    @AfterEach
    void workspaceContainsNoPerSubmissionDirectories() throws Exception {
        try (var files = Files.list(tempDir)) {
            assertThat(files.findAny()).isEmpty();
        }
    }

    @Test
    void returnsAcForCorrectPython() {
        JudgeService.JudgeResult result = judge(
                "python",
                "a, b = map(int, input().split())\nprint(a + b)",
                "[{\"input\":\"1 2\\n\",\"output\":\"3\\n\"}]",
                1500);

        assertThat(result.verdict).isEqualTo("AC");
        assertThat(result.passed).isEqualTo(1);
        assertThat(result.total).isEqualTo(1);
    }

    @Test
    void returnsWaForWrongOutput() {
        JudgeService.JudgeResult result = judge(
                "python", "print(0)",
                "[{\"input\":\"\",\"output\":\"1\"}]", 1000);

        assertThat(result.verdict).isEqualTo("WA");
        assertThat(result.failedCase).isEqualTo(1);
    }

    @Test
    void returnsTleForBusyLoop() {
        JudgeService.JudgeResult result = judge(
                "python", "while True:\n    pass",
                "[{\"input\":\"\",\"output\":\"\"}]", 150);

        assertThat(result.verdict).isEqualTo("TLE");
    }

    @Test
    void returnsReForRuntimeFailure() {
        JudgeService.JudgeResult result = judge(
                "python", "raise RuntimeError('boom')",
                "[{\"input\":\"\",\"output\":\"\"}]", 1000);

        assertThat(result.verdict).isEqualTo("RE");
    }

    @Test
    void returnsCeForInvalidCpp() {
        JudgeService.JudgeResult result = judge(
                "cpp", "int main( {",
                "[{\"input\":\"\",\"output\":\"\"}]", 1000);

        assertThat(result.verdict).isEqualTo("CE");
    }

    @Test
    void cAc() {
        JudgeService.JudgeResult result = judge(
                "c",
                "#include <stdio.h>\nint main(void) { int a,b; scanf(\"%d %d\", &a, &b); printf(\"%d\\n\", a+b); }",
                "[{\"input\":\"4 6\\n\",\"output\":\"10\\n\"}]", 2000);

        assertThat(result.verdict).isEqualTo("AC");
    }

    @Test
    void cpp17Ac() {
        JudgeService.JudgeResult result = judge(
                "cpp",
                "#include <iostream>\nint main() { int a,b; std::cin >> a >> b; std::cout << a+b << '\\n'; }",
                "[{\"input\":\"7 8\\n\",\"output\":\"15\\n\"}]", 2000);

        assertThat(result.verdict).isEqualTo("AC");
    }

    @Test
    void unsupportedLanguageReturnsControlledCe() {
        JudgeService.JudgeResult result = judge(
                "ruby", "puts 1",
                "[{\"input\":\"\",\"output\":\"1\"}]", 1000);

        assertThat(result.verdict).isEqualTo("CE");
        assertThat(result.message).contains("不支持的语言");
        assertThat(result.total).isEqualTo(1);
    }

    @Test
    void damagedTestCaseJsonReturnsSystemError() {
        JudgeService.JudgeResult result = judge(
                "python", "print(1)", "{damaged", 1000);

        assertThat(result.verdict).isEqualTo("SE");
        assertThat(result.message).contains("测试点数据损坏");
    }

    @Test
    void emptyTestCasesReturnSystemErrorInsteadOfAc() {
        JudgeService.JudgeResult result = judge(
                "python", "print(1)", "[]", 1000);

        assertThat(result.verdict).isEqualTo("SE");
        assertThat(result.passed).isZero();
        assertThat(result.total).isZero();
        assertThat(result.message).isEqualTo("No test cases configured");
    }

    @Test
    void javaAc() {
        JudgeService.JudgeResult result = judge(
                "java",
                "public class Main { public static void main(String[] args) { System.out.println(7); } }",
                "[{\"input\":\"\",\"output\":\"7\"}]", 2000);

        assertThat(result.verdict)
                .withFailMessage("Java judge failed: %s", result.message)
                .isEqualTo("AC");
        assertThat(result.passed).isEqualTo(1);
    }

    @Test
    void parsedNullTestCasesReturnTheSameControlledSystemError() {
        JudgeService.JudgeResult result = judge(
                "python", "print(1)", "null", 1000);

        assertThat(result.verdict).isEqualTo("SE");
        assertThat(result.message).isEqualTo("No test cases configured");
    }

    @Test
    void javaWa() {
        JudgeService.JudgeResult result = judge(
                "java",
                "public class Main { public static void main(String[] args) { System.out.println(0); } }",
                "[{\"input\":\"\",\"output\":\"1\"}]", 3000);

        assertThat(result.verdict).isEqualTo("WA");
        assertThat(result.message).contains("答案错误");
    }

    @Test
    void javaCe() {
        JudgeService.JudgeResult result = judge(
                "java",
                "public class Main { public static void main(String[] args) { not valid java } }",
                "[{\"input\":\"\",\"output\":\"\"}]", 3000);

        assertThat(result.verdict).isEqualTo("CE");
        assertThat(result.message).containsIgnoringCase("error");
    }

    @Test
    void javaReReportsTheExitCode() {
        JudgeService.JudgeResult result = judge(
                "java",
                """
                        public class Main {
                            public static void main(String[] args) {
                                throw new RuntimeException("boom");
                            }
                        }
                        """,
                "[{\"input\":\"\",\"output\":\"\"}]", 3000);

        assertThat(result.verdict).isEqualTo("RE");
        assertThat(result.message).contains("退出码 1");
    }

    @Test
    void javaTle() {
        JudgeService.JudgeResult result = judge(
                "java",
                "public class Main { public static void main(String[] args) { while (true) { } } }",
                "[{\"input\":\"\",\"output\":\"\"}]", 300);

        assertThat(result.verdict).isEqualTo("TLE");
        assertThat(result.message).contains("运行超时");
    }

    @Test
    void javascriptAc() {
        JudgeService.JudgeResult result = judge(
                "javascript",
                "const [a,b] = require('fs').readFileSync(0, 'utf8').trim().split(/\\s+/).map(Number); console.log(a+b);",
                "[{\"input\":\"5 8\\n\",\"output\":\"13\\n\"}]", 3000);

        assertThat(result.verdict).isEqualTo("AC");
        assertThat(result.passed).isEqualTo(1);
    }

    @Test
    void javascriptWa() {
        JudgeService.JudgeResult result = judge(
                "javascript", "console.log(0);",
                "[{\"input\":\"\",\"output\":\"1\"}]", 3000);

        assertThat(result.verdict).isEqualTo("WA");
        assertThat(result.message).contains("答案错误");
    }

    @Test
    void javascriptReReportsTheExitCode() {
        JudgeService.JudgeResult result = judge(
                "javascript", "throw new Error('boom');",
                "[{\"input\":\"\",\"output\":\"\"}]", 3000);

        assertThat(result.verdict).isEqualTo("RE");
        assertThat(result.message).contains("退出码 1");
    }

    @Test
    void javascriptTle() {
        JudgeService.JudgeResult result = judge(
                "javascript", "while (true) { }",
                "[{\"input\":\"\",\"output\":\"\"}]", 300);

        assertThat(result.verdict).isEqualTo("TLE");
        assertThat(result.message).contains("运行超时");
    }

    @Test
    void allDeclaredLanguageRuntimesAreAvailable() {
        assertThat(SandboxLanguage.values())
                .extracting(SandboxLanguage::platformId)
                .containsExactly("python", "javascript", "c", "cpp", "java");

        LegacyProcessRunner runner = new LegacyProcessRunner();
        List<List<String>> versionCommands = List.of(
                List.of("python3", "--version"),
                List.of("node", "--version"),
                List.of("gcc", "--version"),
                List.of("g++", "--version"),
                List.of("javac", "-version"),
                List.of("java", "-version")
        );
        for (List<String> command : versionCommands) {
            LegacyProcessResult result = runner.run(command, "", 3000, 0, 1_048_576, tempDir);
            String diagnostic = result.stdout() + result.stderr();
            assertThat(result.exitCode())
                    .as("%s must be installed: %s", command.getFirst(), diagnostic)
                    .isZero();
            assertThat(diagnostic).as("%s version output", command.getFirst()).isNotBlank();
            if ("node".equals(command.getFirst())) {
                assertThat(diagnostic.trim()).startsWith("v22.");
            }
        }
    }

    private JudgeService.JudgeResult judge(
            String language, String code, String cases, long timeLimitMs) {
        return judgeService.judge(language, code, timeLimitMs, 262144, cases);
    }
}

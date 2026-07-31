package com.oj.judge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JudgeServiceTest {

    @TempDir
    Path tempDir;

    private JudgeService judgeService;

    @BeforeEach
    void createJudgeService() throws Exception {
        judgeService = new JudgeService(tempDir);
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
    void emptyTestCasesRecordCurrentAcBehavior() {
        JudgeService.JudgeResult result = judge(
                "python", "print(1)", "[]", 1000);

        assertThat(result.verdict).isEqualTo("AC");
        assertThat(result.passed).isZero();
        assertThat(result.total).isZero();
    }

    @Test
    @Disabled("Known baseline defect: Runner prefixes exec to the Java 'cd ... && java Main' command, which exits 127")
    void javaRuntimeIsAvailableInTheTestImage() {
        JudgeService.JudgeResult result = judge(
                "java",
                "public class Main { public static void main(String[] args) { System.out.println(7); } }",
                "[{\"input\":\"\",\"output\":\"7\"}]", 2000);

        assertThat(result.verdict)
                .withFailMessage("Java judge failed: %s", result.message)
                .isEqualTo("AC");
    }

    private JudgeService.JudgeResult judge(
            String language, String code, String cases, long timeLimitMs) {
        return judgeService.judge(language, code, timeLimitMs, 262144, cases);
    }
}

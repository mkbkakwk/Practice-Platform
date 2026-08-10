package com.oj.runner.execution.linux;

import com.oj.runner.config.LinuxSandboxProperties;
import com.oj.runner.language.LanguageProfileRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class LinuxSandboxPreflightSelfTestTest {

    private SandboxWorkspaceManager workspaceManager;
    private NsJailConfigWriter configWriter;
    private SandboxProcessLauncher launcher;
    private LinuxSandboxPreflight preflight;

    @BeforeEach
    void setUp() throws Exception {
        workspaceManager = mock(SandboxWorkspaceManager.class);
        configWriter = mock(NsJailConfigWriter.class);
        launcher = mock(SandboxProcessLauncher.class);
        SandboxWorkspace workspace = new SandboxWorkspace(
                Path.of("/runner-self-test"),
                Path.of("/runner-self-test/workspace"),
                Path.of("/runner-self-test/metadata"));
        when(workspaceManager.create(anyString())).thenReturn(workspace);
        when(configWriter.write(any(), any(), anyString(), anyLong(), anyLong()))
                .thenReturn(Path.of("/runner-self-test/metadata/nsjail-self-test.cfg"));
        when(configWriter.logPath(any(), anyString()))
                .thenReturn(Path.of("/runner-self-test/metadata/nsjail-self-test.log"));
        preflight = new LinuxSandboxPreflight(
                new LinuxSandboxProperties(),
                new LanguageProfileRegistry(),
                workspaceManager,
                configWriter,
                new LanguageCommandResolver(),
                launcher);
    }

    @Test
    void successfulSelfTestPassesWithoutFailureWarning(CapturedOutput output) {
        when(launcher.launch(any())).thenReturn(result(
                SandboxTermination.COMPLETED, 0, 12, 2048, "", ""));

        assertThat(preflight.executeSelfTest()).isTrue();
        assertThat(output.getAll()).doesNotContain("Linux sandbox self-test failed");
    }

    @Test
    void nonzeroExitLogsBoundedExecutionDetails(CapturedOutput output) {
        when(launcher.launch(any())).thenReturn(result(
                SandboxTermination.COMPLETED, 7, 31, 4096,
                "self-test stderr", "nsjail diagnostic"));

        assertThat(preflight.executeSelfTest()).isFalse();
        assertThat(output.getAll())
                .contains("termination=COMPLETED", "exitCode=7", "timeMs=31", "memoryKb=4096")
                .contains("stderr=self-test stderr", "diagnostic=nsjail diagnostic");
    }

    @Test
    void sandboxErrorLogsItsTerminationAndDiagnostic(CapturedOutput output) {
        when(launcher.launch(any())).thenReturn(result(
                SandboxTermination.SANDBOX_ERROR, -1, 3, 0,
                "", "namespace setup failed"));

        assertThat(preflight.executeSelfTest()).isFalse();
        assertThat(output.getAll())
                .contains("termination=SANDBOX_ERROR", "exitCode=-1")
                .contains("diagnostic=namespace setup failed");
    }

    @Test
    void stderrAndDiagnosticAreTruncatedAndKeptOnOneLogLine(CapturedOutput output) {
        String stderrTail = "stderr-tail-must-not-be-logged";
        String diagnosticTail = "diagnostic-tail-must-not-be-logged";
        when(launcher.launch(any())).thenReturn(result(
                SandboxTermination.SANDBOX_ERROR, -1, 1, 0,
                "e".repeat(9000) + "\n" + stderrTail,
                "d".repeat(9000) + "\r\n" + diagnosticTail));

        assertThat(preflight.executeSelfTest()).isFalse();
        assertThat(output.getAll())
                .contains("...[truncated]")
                .doesNotContain(stderrTail, diagnosticTail);
    }

    @Test
    void exceptionLogsOnlyTypeAndBoundedSafeMessage(CapturedOutput output) throws Exception {
        when(workspaceManager.create(anyString()))
                .thenThrow(new IOException("safe failure\nRUNNER_TOKEN=runner-secret "
                        + "Authorization: Bearer bearer-secret"));

        assertThat(preflight.executeSelfTest()).isFalse();
        assertThat(output.getAll())
                .contains("self-test execution failed")
                .contains("exceptionType=IOException")
                .contains("message=safe failure\\nRUNNER_TOKEN=[redacted] "
                        + "Authorization: Bearer [redacted]")
                .doesNotContain("runner-secret", "bearer-secret");
    }

    @Test
    void runtimeExceptionUsesTheSameSafeFailurePath(CapturedOutput output) {
        when(launcher.launch(any())).thenThrow(new IllegalStateException("launcher runtime failure"));

        assertThat(preflight.executeSelfTest()).isFalse();
        assertThat(output.getAll())
                .contains("self-test execution failed")
                .contains("exceptionType=IllegalStateException")
                .contains("message=launcher runtime failure");
    }

    private NsJailExecutionResult result(
            SandboxTermination termination,
            int exitCode,
            long timeMs,
            long memoryKb,
            String stderr,
            String diagnostic) {
        return new NsJailExecutionResult(
                termination, exitCode, "ignored stdout", stderr, timeMs, memoryKb, diagnostic);
    }
}

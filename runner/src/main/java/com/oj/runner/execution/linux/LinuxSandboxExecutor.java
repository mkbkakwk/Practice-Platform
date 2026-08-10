package com.oj.runner.execution.linux;

import com.oj.runner.api.RunnerCaseRequest;
import com.oj.runner.api.RunnerCaseResult;
import com.oj.runner.api.RunnerCompileResult;
import com.oj.runner.api.RunnerJobResponse;
import com.oj.runner.api.RunnerStatus;
import com.oj.runner.execution.RunnerResponses;
import com.oj.runner.execution.SandboxExecutor;
import com.oj.runner.language.RunnerJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile("!runner-contract-test")
@ConditionalOnProperty(prefix = "runner.sandbox", name = "mode", havingValue = "linux")
public class LinuxSandboxExecutor implements SandboxExecutor {

    private final LinuxSandboxPreflight preflight;
    private final SandboxWorkspaceManager workspaceManager;
    private final NsJailConfigWriter configWriter;
    private final LanguageCommandResolver commandResolver;
    private final SandboxProcessLauncher launcher;

    public LinuxSandboxExecutor(
            LinuxSandboxPreflight preflight,
            SandboxWorkspaceManager workspaceManager,
            NsJailConfigWriter configWriter,
            LanguageCommandResolver commandResolver,
            SandboxProcessLauncher launcher) {
        this.preflight = preflight;
        this.workspaceManager = workspaceManager;
        this.configWriter = configWriter;
        this.commandResolver = commandResolver;
        this.launcher = launcher;
    }

    @Override
    public RunnerJobResponse execute(RunnerJob job) {
        String requestId = job.request().requestId();
        if (!available()) {
            return RunnerResponses.systemError(requestId, "Linux sandbox is unavailable");
        }

        SandboxWorkspace workspace = null;
        RunnerJobResponse response;
        boolean cleanupFailed = false;
        try {
            workspace = workspaceManager.create(requestId);
            workspaceManager.writeSource(workspace, job.profile().sourceFilename(), job.request().sourceCode());
            response = executeInWorkspace(job, workspace);
        } catch (IOException | RuntimeException exception) {
            response = RunnerResponses.systemError(requestId, "Linux sandbox execution failed");
        } finally {
            if (workspace != null) {
                try {
                    workspaceManager.cleanup(workspace);
                } catch (IOException exception) {
                    cleanupFailed = true;
                }
            }
        }
        return cleanupFailed
                ? RunnerResponses.systemError(requestId, "Sandbox workspace cleanup failed")
                : response;
    }

    @Override
    public boolean available() {
        return preflight.availability().supported();
    }

    private RunnerJobResponse executeInWorkspace(RunnerJob job, SandboxWorkspace workspace) throws IOException {
        var limits = job.request().limits();
        Path compileConfig = configWriter.write(
                workspace, job.profile(), "compile", limits.compileTimeMs(), limits.memoryMb());
        NsJailExecutionResult compileExecution = launcher.launch(new NsJailInvocation(
                SandboxPhase.COMPILE,
                compileConfig,
                configWriter.logPath(workspace, "compile"),
                workspace.files(),
                commandResolver.compile(job.profile(), limits.memoryMb()),
                new byte[0],
                limits.compileTimeMs(),
                limits.memoryMb(),
                limits.outputLimitBytes()));
        RunnerCompileResult compile = compileResult(compileExecution);
        if (compile.status() != RunnerStatus.OK) {
            return new RunnerJobResponse(job.request().requestId(), compile, List.of(), compile.message());
        }

        List<RunnerCaseResult> cases = new ArrayList<>();
        for (int index = 0; index < job.request().cases().size(); index++) {
            RunnerCaseRequest requestCase = job.request().cases().get(index);
            String phaseId = "case-" + index;
            Path runConfig = configWriter.write(
                    workspace, job.profile(), phaseId, limits.runTimeMs(), limits.memoryMb());
            NsJailExecutionResult execution = launcher.launch(new NsJailInvocation(
                    SandboxPhase.RUN,
                    runConfig,
                    configWriter.logPath(workspace, phaseId),
                    workspace.files(),
                    commandResolver.run(job.profile(), limits.memoryMb()),
                    requestCase.stdin().getBytes(StandardCharsets.UTF_8),
                    limits.runTimeMs(),
                    limits.memoryMb(),
                    limits.outputLimitBytes()));
            RunnerCaseResult result = caseResult(requestCase.caseId(), execution);
            cases.add(result);
            if (result.status() != RunnerStatus.OK) {
                break;
            }
        }
        return new RunnerJobResponse(job.request().requestId(), compile, List.copyOf(cases), "");
    }

    private RunnerCompileResult compileResult(NsJailExecutionResult execution) {
        RunnerStatus status = switch (execution.termination()) {
            case COMPLETED -> execution.exitCode() == 0 ? RunnerStatus.OK : RunnerStatus.COMPILE_ERROR;
            case TIME_LIMIT -> RunnerStatus.TIME_LIMIT_EXCEEDED;
            case MEMORY_LIMIT -> RunnerStatus.MEMORY_LIMIT_EXCEEDED;
            case OUTPUT_LIMIT, WORKSPACE_LIMIT -> RunnerStatus.OUTPUT_LIMIT_EXCEEDED;
            case SANDBOX_ERROR -> RunnerStatus.SYSTEM_ERROR;
        };
        String message = status == RunnerStatus.SYSTEM_ERROR ? "Compile sandbox failed" : "";
        return new RunnerCompileResult(
                status, execution.exitCode(), execution.stderr(), execution.timeMs(), message);
    }

    private RunnerCaseResult caseResult(String caseId, NsJailExecutionResult execution) {
        RunnerStatus status = switch (execution.termination()) {
            case COMPLETED -> execution.exitCode() == 0 ? RunnerStatus.OK : RunnerStatus.RUNTIME_ERROR;
            case TIME_LIMIT -> RunnerStatus.TIME_LIMIT_EXCEEDED;
            case MEMORY_LIMIT -> RunnerStatus.MEMORY_LIMIT_EXCEEDED;
            case OUTPUT_LIMIT, WORKSPACE_LIMIT -> RunnerStatus.OUTPUT_LIMIT_EXCEEDED;
            case SANDBOX_ERROR -> RunnerStatus.SYSTEM_ERROR;
        };
        String message = status == RunnerStatus.SYSTEM_ERROR ? "Runtime sandbox failed" : "";
        return new RunnerCaseResult(
                caseId,
                status,
                execution.exitCode(),
                execution.stdout(),
                execution.stderr(),
                execution.timeMs(),
                execution.memoryKb(),
                message);
    }
}

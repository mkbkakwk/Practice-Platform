package com.oj.sandbox.local;

import com.oj.sandbox.SandboxCaseResult;
import com.oj.sandbox.SandboxClient;
import com.oj.sandbox.SandboxClientException;
import com.oj.sandbox.SandboxCompileResult;
import com.oj.sandbox.SandboxRequest;
import com.oj.sandbox.SandboxRequestValidator;
import com.oj.sandbox.SandboxResult;
import com.oj.sandbox.SandboxStatus;
import com.oj.sandbox.SandboxTestCase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Compatibility adapter for the historical in-Worker execution path.
 *
 * <p>This implementation is NOT a security sandbox. It exists only to preserve
 * current behaviour until a dedicated Linux Runner becomes the isolation boundary.</p>
 */
public final class LegacyLocalSandboxClient implements SandboxClient {

    private final Path workspace;
    private final int maxSourceBytes;
    private final int maxStdinBytes;
    private final LegacyProcessRunner processRunner;

    public LegacyLocalSandboxClient(Path workspace, int maxSourceBytes, int maxStdinBytes) {
        this(workspace, maxSourceBytes, maxStdinBytes, new LegacyProcessRunner());
    }

    LegacyLocalSandboxClient(
            Path workspace,
            int maxSourceBytes,
            int maxStdinBytes,
            LegacyProcessRunner processRunner) {
        this.workspace = workspace.toAbsolutePath().normalize();
        this.maxSourceBytes = maxSourceBytes;
        this.maxStdinBytes = maxStdinBytes;
        this.processRunner = processRunner;
        try {
            Files.createDirectories(this.workspace);
        } catch (IOException exception) {
            throw new SandboxClientException("Cannot create legacy sandbox workspace", exception);
        }
    }

    @Override
    public SandboxResult execute(SandboxRequest request) {
        SandboxRequestValidator.validate(request, maxSourceBytes, maxStdinBytes);
        LegacyLanguageProfile profile = LegacyLanguageProfile.of(request.language());
        Path workDirectory = workspace.resolve(request.requestId()).normalize();
        if (!workDirectory.startsWith(workspace)) {
            throw new SandboxClientException("Legacy sandbox workspace escaped its root");
        }

        try {
            Files.createDirectory(workDirectory);
            Path source = workDirectory.resolve("Main." + profile.extension());
            Path output = workDirectory.resolve("main_out");
            Files.writeString(source, request.sourceCode());

            SandboxCompileResult compile = compile(request, profile, source, output, workDirectory);
            if (compile.status() != SandboxStatus.OK) {
                return new SandboxResult(request.requestId(), compile, List.of(), compile.message());
            }

            List<SandboxCaseResult> cases = new ArrayList<>();
            for (SandboxTestCase testCase : request.cases()) {
                SandboxCaseResult caseResult = runCase(request, profile, source, output, workDirectory, testCase);
                cases.add(caseResult);
                if (caseResult.status() != SandboxStatus.OK) {
                    break;
                }
            }
            return new SandboxResult(request.requestId(), compile, List.copyOf(cases), "");
        } catch (Exception exception) {
            SandboxCompileResult failure = new SandboxCompileResult(
                    SandboxStatus.SYSTEM_ERROR, -1, "", 0, safeMessage(exception));
            return new SandboxResult(request.requestId(), failure, List.of(), failure.message());
        } finally {
            cleanup(workDirectory);
        }
    }

    private SandboxCompileResult compile(
            SandboxRequest request,
            LegacyLanguageProfile profile,
            Path source,
            Path output,
            Path workDirectory) {
        List<String> command = profile.compileCommand(source, output);
        if (command == null) {
            return new SandboxCompileResult(SandboxStatus.OK, 0, "", 0, "");
        }
        LegacyProcessResult result = processRunner.run(
                command, "", request.limits().compileTimeMs(), 0,
                request.limits().outputLimitBytes(), workDirectory);
        SandboxStatus status = processStatus(result, true);
        String message = status == SandboxStatus.OK ? "" : firstNonBlank(result.stderr(), "Compilation failed");
        return new SandboxCompileResult(status, result.exitCode(), result.stderr(), result.elapsedMs(), message);
    }

    private SandboxCaseResult runCase(
            SandboxRequest request,
            LegacyLanguageProfile profile,
            Path source,
            Path output,
            Path workDirectory,
            SandboxTestCase testCase) {
        long memoryLimitKb = request.limits().memoryMb() * 1_024;
        long processMemoryLimitKb = switch (request.language()) {
            case JAVA, JAVASCRIPT -> 0;
            default -> memoryLimitKb * 2;
        };
        LegacyProcessResult result = processRunner.run(
                profile.runCommand(source, output), testCase.stdin(), request.limits().runTimeMs(),
                processMemoryLimitKb, request.limits().outputLimitBytes(), workDirectory);
        SandboxStatus status = processStatus(result, false);
        return new SandboxCaseResult(
                testCase.caseId(), status, result.exitCode(), result.stdout(), result.stderr(),
                result.elapsedMs(), status == SandboxStatus.MEMORY_LIMIT_EXCEEDED ? memoryLimitKb : 0,
                status == SandboxStatus.OK ? "" : firstNonBlank(result.stderr(), status.name()));
    }

    private SandboxStatus processStatus(LegacyProcessResult result, boolean compile) {
        if (result.timedOut()) {
            return SandboxStatus.TIME_LIMIT_EXCEEDED;
        }
        if (result.outputExceeded()) {
            return SandboxStatus.OUTPUT_LIMIT_EXCEEDED;
        }
        if (result.memoryError()) {
            return SandboxStatus.MEMORY_LIMIT_EXCEEDED;
        }
        if (result.exitCode() == -1) {
            return SandboxStatus.SYSTEM_ERROR;
        }
        if (result.exitCode() != 0) {
            return compile ? SandboxStatus.COMPILE_ERROR : SandboxStatus.RUNTIME_ERROR;
        }
        return SandboxStatus.OK;
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred.trim();
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private void cleanup(Path directory) {
        try {
            if (Files.exists(directory)) {
                try (var paths = Files.walk(directory)) {
                    paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
                }
            }
        } catch (IOException ignored) {
        }
    }
}

package com.oj.runner.execution.linux;

import com.oj.runner.api.RunnerCaseRequest;
import com.oj.runner.api.RunnerJobRequest;
import com.oj.runner.api.RunnerLanguage;
import com.oj.runner.api.RunnerLimitsRequest;
import com.oj.runner.api.RunnerStatus;
import com.oj.runner.config.LinuxSandboxProperties;
import com.oj.runner.language.LanguageProfileRegistry;
import com.oj.runner.language.RunnerJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LinuxSandboxExecutorTest {

    @TempDir
    Path temporaryDirectory;

    private LinuxSandboxPreflight preflight;
    private LinuxSandboxProperties properties;
    private ExecutionCgroupManager cgroupManager;
    private List<ExecutionCgroupLease> cgroupLeases;

    @BeforeEach
    void setUp() throws Exception {
        preflight = mock(LinuxSandboxPreflight.class);
        when(preflight.availability()).thenReturn(new SandboxAvailability(true, List.of()));
        properties = new LinuxSandboxProperties();
        Path workspaceRoot = Files.createDirectory(temporaryDirectory.resolve("jobs"));
        properties.setWorkspaceRoot(workspaceRoot.toString());
        properties.setRootfs("/srv/oj-sandbox-runner/rootfs");
        properties.setSeccompPolicy("/etc/oj-sandbox-runner/nsjail-seccomp.policy");
        properties.setCgroupV2Mount("/sys/fs/cgroup/system.slice/oj-sandbox-runner.service");
        cgroupManager = mock(ExecutionCgroupManager.class);
        cgroupLeases = new ArrayList<>();
        AtomicInteger sequence = new AtomicInteger();
        when(cgroupManager.allocate()).thenAnswer(invocation -> {
            ExecutionCgroupLease lease = mock(ExecutionCgroupLease.class);
            String id = "%032x".formatted(sequence.incrementAndGet());
            when(lease.path()).thenReturn(Path.of(properties.getCgroupV2Mount()).resolve("RUNNER." + id));
            cgroupLeases.add(lease);
            return lease;
        });
    }

    @Test
    void compilesOnceAndRunsCasesInOrder() {
        QueueLauncher launcher = new QueueLauncher(
                completed(0, "", "", 4),
                completed(0, "one\n", "", 2),
                completed(0, "two\n", "", 3));
        var response = executor(launcher).execute(job(List.of(
                new RunnerCaseRequest("one", "1\n"), new RunnerCaseRequest("two", "2\n"))));

        assertThat(response.compile().status()).isEqualTo(RunnerStatus.OK);
        assertThat(response.cases()).extracting(result -> result.caseId())
                .containsExactly("one", "two");
        assertThat(response.cases()).extracting(result -> result.stdout())
                .containsExactly("one\n", "two\n");
        assertThat(launcher.phases).containsExactly(SandboxPhase.COMPILE, SandboxPhase.RUN, SandboxPhase.RUN);
        assertThat(cgroupLeases).hasSize(3).allSatisfy(lease -> {
            try {
                verify(lease).close();
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        assertThat(workspaceRoot()).isEmptyDirectory();
    }

    @Test
    void compileAndRuntimeLimitsMapToProtocolStatuses() {
        QueueLauncher compileOom = new QueueLauncher(new NsJailExecutionResult(
                SandboxTermination.MEMORY_LIMIT, 137, "", "", 10, 65536, ""));
        assertThat(executor(compileOom).execute(job(oneCase())).compile().status())
                .isEqualTo(RunnerStatus.MEMORY_LIMIT_EXCEEDED);

        QueueLauncher runtimeOutput = new QueueLauncher(
                completed(0, "", "", 1),
                new NsJailExecutionResult(SandboxTermination.OUTPUT_LIMIT, 137, "bounded", "", 2, 1024, ""));
        assertThat(executor(runtimeOutput).execute(job(oneCase())).cases().getFirst().status())
                .isEqualTo(RunnerStatus.OUTPUT_LIMIT_EXCEEDED);

        QueueLauncher runtimeError = new QueueLauncher(
                completed(0, "", "", 1),
                completed(7, "", "expected runtime error", 2));
        assertThat(executor(runtimeError).execute(job(oneCase())).cases().getFirst().status())
                .isEqualTo(RunnerStatus.RUNTIME_ERROR);
        assertThat(workspaceRoot()).isEmptyDirectory();
    }

    @Test
    void executionCgroupCleanupFailureFailsClosed() throws Exception {
        ExecutionCgroupLease lease = mock(ExecutionCgroupLease.class);
        when(lease.path()).thenReturn(Path.of(properties.getCgroupV2Mount())
                .resolve("RUNNER.aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        doThrow(new java.io.IOException("simulated cleanup failure")).when(lease).close();
        when(cgroupManager.allocate()).thenReturn(lease);

        var response = executor(new QueueLauncher(completed(0, "", "", 1))).execute(job(oneCase()));

        assertThat(response.compile().status()).isEqualTo(RunnerStatus.SYSTEM_ERROR);
        assertThat(workspaceRoot()).isEmptyDirectory();
    }

    @Test
    void unavailablePreflightFailsClosedWithoutLaunching() {
        when(preflight.availability()).thenReturn(new SandboxAvailability(false, List.of("unsupported")));
        QueueLauncher launcher = new QueueLauncher();
        var response = executor(launcher).execute(job(oneCase()));
        assertThat(response.compile().status()).isEqualTo(RunnerStatus.SYSTEM_ERROR);
        assertThat(launcher.phases).isEmpty();
        verifyNoInteractions(cgroupManager);
    }

    private LinuxSandboxExecutor executor(SandboxProcessLauncher launcher) {
        return new LinuxSandboxExecutor(
                preflight,
                new SandboxWorkspaceManager(properties),
                new NsJailConfigWriter(properties),
                cgroupManager,
                new LanguageCommandResolver(),
                launcher);
    }

    private RunnerJob job(List<RunnerCaseRequest> cases) {
        RunnerJobRequest request = new RunnerJobRequest(
                "11111111-1111-4111-8111-111111111111",
                RunnerLanguage.PYTHON,
                "print(input())",
                new RunnerLimitsRequest(5000, 1000, 256, 1024),
                cases);
        return new RunnerJob(request, new LanguageProfileRegistry().require(request.language()));
    }

    private List<RunnerCaseRequest> oneCase() {
        return List.of(new RunnerCaseRequest("one", "1\n"));
    }

    private Path workspaceRoot() {
        return Path.of(properties.getWorkspaceRoot());
    }

    private NsJailExecutionResult completed(int exit, String stdout, String stderr, long timeMs) {
        return new NsJailExecutionResult(
                SandboxTermination.COMPLETED, exit, stdout, stderr, timeMs, 1024, "");
    }

    private static final class QueueLauncher implements SandboxProcessLauncher {
        private final Queue<NsJailExecutionResult> results;
        private final List<SandboxPhase> phases = new java.util.ArrayList<>();

        private QueueLauncher(NsJailExecutionResult... results) {
            this.results = new ArrayDeque<>(List.of(results));
        }

        @Override
        public NsJailExecutionResult launch(NsJailInvocation invocation) {
            phases.add(invocation.phase());
            return results.remove();
        }
    }
}

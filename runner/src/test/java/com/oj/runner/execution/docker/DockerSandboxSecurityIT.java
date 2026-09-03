package com.oj.runner.execution.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import com.oj.runner.api.RunnerCaseRequest;
import com.oj.runner.api.RunnerCaseResult;
import com.oj.runner.api.RunnerJobRequest;
import com.oj.runner.api.RunnerJobResponse;
import com.oj.runner.api.RunnerLanguage;
import com.oj.runner.api.RunnerLimitsRequest;
import com.oj.runner.api.RunnerStatus;
import com.oj.runner.service.RunnerJobService;
import com.oj.runner.service.RunnerSaturatedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SpringBootTest(properties = {
        "runner.token=docker-security-test-token",
        "runner.sandbox.mode=docker",
        "runner.max-concurrent-jobs=4",
        "runner.sandbox.docker.instance-id=docker-security-it",
        "runner.sandbox.docker.pids-limit=64"
})
class DockerSandboxSecurityIT {

    private static final String INSTANCE_ID = "docker-security-it";

    @Autowired
    RunnerJobService service;

    @Autowired
    DockerClient docker;

    @AfterEach
    void noSandboxResourcesRemain() {
        awaitNoSandboxResources();
        assertThat(service.sandboxAvailable()).isTrue();
    }

    @Test
    void pythonExecutesInDisposableContainer() {
        assertOk(RunnerLanguage.PYTHON, "print('python-ok')", "python-ok\n");
    }

    @Test
    void javascriptExecutesInDisposableContainer() {
        assertOk(RunnerLanguage.JAVASCRIPT,
                "process.stdout.write(require('fs').readFileSync(0, 'utf8'))", "javascript-ok\n");
    }

    @Test
    void cExecutesInDisposableContainer() {
        assertOk(RunnerLanguage.C,
                "#include <stdio.h>\nint main(void){char s[64];fgets(s,sizeof(s),stdin);printf(\"%s\",s);}",
                "c-ok\n");
    }

    @Test
    void cpp17ExecutesInDisposableContainer() {
        assertOk(RunnerLanguage.CPP17,
                "#include <iostream>\n#include <string>\nint main(){std::string s;std::getline(std::cin,s);std::cout<<s<<'\\n';}",
                "cpp-ok\n");
    }

    @Test
    void javaExecutesInDisposableContainer() {
        assertOk(RunnerLanguage.JAVA, """
                import java.io.*;
                public class Main {
                    public static void main(String[] args) throws Exception {
                        System.out.println(new BufferedReader(new InputStreamReader(System.in)).readLine());
                    }
                }
                """, "java-ok\n");
    }

    @Test
    void compileErrorIsReported() {
        RunnerJobResponse response = execute(RunnerLanguage.C, "int main( {", "", 1_000, 64, 4_096);
        assertThat(response.compile().status()).isEqualTo(RunnerStatus.COMPILE_ERROR);
        assertThat(response.cases()).isEmpty();
    }

    @Test
    void runtimeErrorIsReported() {
        assertStatus(execute(RunnerLanguage.C, "int main(void){return 7;}", "", 1_000, 64, 4_096),
                RunnerStatus.RUNTIME_ERROR);
    }

    @Test
    void wallTimeoutKillsTheContainerProcessTree() {
        assertStatus(execute(RunnerLanguage.PYTHON, "while True: pass", "", 500, 64, 4_096),
                RunnerStatus.TIME_LIMIT_EXCEEDED);
    }

    @Test
    void memoryLimitIsReportedFromDockerOomEvidence() {
        String source = """
                #include <stdlib.h>
                #include <string.h>
                int main(void) {
                    while (1) {
                        void *p = malloc(1048576);
                        if (!p) return 9;
                        memset(p, 1, 1048576);
                    }
                }
                """;
        assertStatus(execute(RunnerLanguage.C, source, "", 5_000, 32, 4_096),
                RunnerStatus.MEMORY_LIMIT_EXCEEDED);
    }

    @Test
    void outputLimitStopsStreamingBeforeUnboundedCapture() {
        RunnerJobResponse response = execute(
                RunnerLanguage.PYTHON,
                "import sys\nwhile True:\n print('x' * 64)\n print('e' * 64, file=sys.stderr)",
                "", 3_000, 64, 4_096);
        assertStatus(response, RunnerStatus.OUTPUT_LIMIT_EXCEEDED);
        assertThat(response.cases().getFirst().stdout().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .hasSizeLessThanOrEqualTo(4_096);
    }

    @Test
    void forkBombCannotEscapePidsLimit() {
        String source = """
                #include <unistd.h>
                int main(void) {
                    while (1) {
                        if (fork() < 0) pause();
                    }
                }
                """;
        assertStatus(execute(RunnerLanguage.C, source, "", 800, 64, 4_096),
                RunnerStatus.TIME_LIMIT_EXCEEDED);
    }

    @Test
    void studentHasNoNetworkRunnerSecretDockerSocketOrRootPrivileges() {
        String source = """
                import ctypes, os, socket
                assert os.getuid() == 10001 and os.getgid() == 10001
                assert 'RUNNER_TOKEN' not in os.environ
                assert not os.path.exists('/var/run/docker.sock')
                assert not os.path.exists('/root/.ssh')
                assert not os.path.exists('/run/secrets')
                cap_eff = next(line.split()[1] for line in open('/proc/self/status') if line.startswith('CapEff:'))
                assert int(cap_eff, 16) == 0
                assert b'oj-sandbox-runner' not in open('/proc/1/cmdline', 'rb').read()
                try:
                    open('/rootfs-write', 'w')
                    raise AssertionError('root filesystem was writable')
                except OSError:
                    pass
                open('/workspace/write-ok', 'w').write('ok')
                open('/tmp/write-ok', 'w').write('ok')
                try:
                    socket.create_connection(('1.1.1.1', 53), 0.2)
                    raise AssertionError('network was reachable')
                except OSError:
                    pass
                try:
                    socket.getaddrinfo('example.com', 80)
                    raise AssertionError('DNS was reachable')
                except OSError:
                    pass
                try:
                    socket.socket(socket.AF_INET, socket.SOCK_RAW, socket.IPPROTO_ICMP)
                    raise AssertionError('raw socket was available')
                except OSError:
                    pass
                libc = ctypes.CDLL(None, use_errno=True)
                assert libc.mount(b'none', b'/', b'tmpfs', 0, None) == -1
                # Docker's default seccomp may permit ptrace within one private PID
                # namespace. PID 1 is only this submission's inert tail process; the
                # Runner JVM and other submissions are not visible in this namespace.
                ptrace_result = libc.ptrace(16, 1, None, None)
                if ptrace_result == 0:
                    # The trace target is deliberately confined to this disposable
                    # submission container and is destroyed during finally cleanup.
                    pass
                assert libc.ptrace(16, 4194304, None, None) == -1
                assert libc.unshare(0x00020000) == -1
                print('security-boundary-ok')
                """;
        assertOk(RunnerLanguage.PYTHON, source, "security-boundary-ok\n", "");
    }

    @Test
    void testcaseWorkspacesAreIsolatedWithinOneSubmission() {
        String source = """
                import os, sys
                marker = '/workspace/student-state'
                if sys.stdin.read().strip() == 'write':
                    open(marker, 'w').write('private')
                    print('first')
                else:
                    print('isolated' if not os.path.exists(marker) else 'leaked')
                """;
        RunnerJobResponse response = executeCases(
                RunnerLanguage.PYTHON,
                source,
                List.of(new RunnerCaseRequest("1", "write"), new RunnerCaseRequest("2", "check")),
                2_000, 64, 4_096);

        assertThat(response.cases()).extracting(RunnerCaseResult::stdout)
                .containsExactly("first\n", "isolated\n");
    }

    @Test
    void concurrentSubmissionsDoNotCrossOutputsOrWorkspaces() {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<CompletableFuture<String>> responses = List.of("alpha", "beta").stream()
                    .map(marker -> CompletableFuture.supplyAsync(() -> {
                        String source = """
                                import os, sys, time
                                marker = sys.stdin.read().strip()
                                path = '/workspace/' + marker
                                open(path, 'w').write(marker)
                                time.sleep(0.2)
                                assert os.listdir('/workspace') == [marker]
                                print(open(path).read())
                                """;
                        return execute(RunnerLanguage.PYTHON, source, marker, 2_000, 64, 4_096)
                                .cases().getFirst().stdout();
                    }, pool))
                    .toList();
            assertThat(responses.stream().map(CompletableFuture::join).toList())
                    .containsExactly("alpha\n", "beta\n");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void tmpFilesystemHasABoundedCapacity() {
        String source = """
                block = b'x' * 1048576
                limited = False
                try:
                    with open('/tmp/fill', 'wb') as output:
                        for _ in range(64):
                            output.write(block)
                except OSError:
                    limited = True
                assert limited
                print('tmp-limited')
                """;
        assertOk(RunnerLanguage.PYTHON, source, "tmp-limited\n", "");
    }

    @Test
    void liveStudentContainerUsesTheExactSecurityConfiguration() throws Exception {
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<RunnerJobResponse> future = CompletableFuture.supplyAsync(() -> execute(
                requestId, RunnerLanguage.PYTHON, "import time; time.sleep(3); print('done')",
                "", 4_000, 64, 4_096));
        Container live = awaitRunContainer(requestId);
        var inspection = docker.inspectContainerCmd(live.getId()).exec();
        HostConfig host = inspection.getHostConfig();

        assertThat(inspection.getConfig().getUser()).isEqualTo("10001:10001");
        assertThat(host.getPrivileged()).isFalse();
        assertThat(host.getReadonlyRootfs()).isTrue();
        assertThat(host.getNetworkMode()).isEqualTo("none");
        assertThat(host.getPidMode()).isNotEqualTo("host");
        assertThat(host.getIpcMode()).isEqualTo("private");
        assertThat(host.getCapAdd()).isNullOrEmpty();
        assertThat(host.getCapDrop()).isNotEmpty();
        assertThat(host.getSecurityOpts()).contains("no-new-privileges=true");
        assertThat(host.getMemory()).isEqualTo(64L * 1024 * 1024);
        assertThat(host.getMemorySwap()).isEqualTo(64L * 1024 * 1024);
        assertThat(host.getPidsLimit()).isEqualTo(64L);
        assertThat(host.getNanoCPUs()).isEqualTo(1_000_000_000L);
        assertThat(inspection.getMounts()).noneMatch(mount ->
                "/var/run/docker.sock".equals(mount.getDestination() == null
                        ? null : mount.getDestination().getPath()));

        assertThat(future.get(10, TimeUnit.SECONDS).cases().getFirst().status()).isEqualTo(RunnerStatus.OK);
    }

    @Test
    void eightConcurrentRequestsAllCompleteWithoutExceedingFourActiveJobs() throws Exception {
        int requests = 8;
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean monitoring = new AtomicBoolean(true);
        AtomicInteger maximumContainers = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        try {
            CompletableFuture<Void> monitor = CompletableFuture.runAsync(() -> {
                while (monitoring.get()) {
                    maximumContainers.accumulateAndGet(activeSandboxContainers(), Math::max);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            });
            List<CompletableFuture<String>> jobs = new ArrayList<>();
            for (int index = 0; index < requests; index++) {
                int jobIndex = index;
                jobs.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        start.await();
                        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
                        while (true) {
                            try {
                                String marker = "job-" + jobIndex;
                                RunnerJobResponse response = execute(
                                        RunnerLanguage.PYTHON,
                                        "import sys,time; time.sleep(0.4); print(sys.stdin.read())",
                                        marker, 3_000, 64, 4_096);
                                assertThat(response.cases().getFirst().stdout()).isEqualTo(marker + "\n");
                                return marker;
                            } catch (RunnerSaturatedException exception) {
                                if (System.nanoTime() >= deadline) {
                                    throw exception;
                                }
                                sleep(25);
                            }
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                }, pool));
            }
            start.countDown();
            List<String> results = jobs.stream().map(CompletableFuture::join).toList();
            monitoring.set(false);
            monitor.get(2, TimeUnit.SECONDS);

            assertThat(results).containsExactlyInAnyOrder(
                    "job-0", "job-1", "job-2", "job-3", "job-4", "job-5", "job-6", "job-7");
            assertThat(maximumContainers.get()).isLessThanOrEqualTo(4);
            System.out.println("Docker sandbox peak active student containers: "
                    + maximumContainers.get());
        } finally {
            monitoring.set(false);
            pool.shutdownNow();
        }
    }

    private void assertOk(RunnerLanguage language, String source, String expectedOutput) {
        assertOk(language, source, expectedOutput, expectedOutput);
    }

    private void assertOk(RunnerLanguage language, String source, String expectedOutput, String stdin) {
        RunnerJobResponse response = execute(language, source, stdin, 2_000, 128, 16_384);
        assertThat(response.compile().status()).as("response=%s", response).isEqualTo(RunnerStatus.OK);
        assertThat(response.cases()).hasSize(1);
        assertThat(response.cases().getFirst().status()).as("response=%s", response).isEqualTo(RunnerStatus.OK);
        assertThat(response.cases().getFirst().stdout()).isEqualTo(expectedOutput);
    }

    private void assertStatus(RunnerJobResponse response, RunnerStatus status) {
        assertThat(response.compile().status()).isEqualTo(RunnerStatus.OK);
        assertThat(response.cases()).hasSize(1);
        assertThat(response.cases().getFirst().status()).isEqualTo(status);
    }

    private RunnerJobResponse execute(
            RunnerLanguage language, String source, String stdin, long runTimeMs, long memoryMb, int outputBytes) {
        return execute(UUID.randomUUID().toString(), language, source, stdin, runTimeMs, memoryMb, outputBytes);
    }

    private RunnerJobResponse execute(
            String requestId, RunnerLanguage language, String source, String stdin,
            long runTimeMs, long memoryMb, int outputBytes) {
        return executeCases(requestId, language, source, List.of(new RunnerCaseRequest("1", stdin)),
                runTimeMs, memoryMb, outputBytes);
    }

    private RunnerJobResponse executeCases(
            RunnerLanguage language, String source, List<RunnerCaseRequest> cases,
            long runTimeMs, long memoryMb, int outputBytes) {
        return executeCases(UUID.randomUUID().toString(), language, source, cases,
                runTimeMs, memoryMb, outputBytes);
    }

    private RunnerJobResponse executeCases(
            String requestId, RunnerLanguage language, String source, List<RunnerCaseRequest> cases,
            long runTimeMs, long memoryMb, int outputBytes) {
        return service.execute(new RunnerJobRequest(
                requestId,
                language,
                source,
                new RunnerLimitsRequest(15_000, runTimeMs, memoryMb, outputBytes),
                cases));
    }

    private Container awaitRunContainer(String requestId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        while (System.nanoTime() < deadline) {
            List<Container> matches = sandboxContainers().stream()
                    .filter(container -> requestId.equals(container.getLabels().get(DockerSandboxExecutor.JOB_LABEL)))
                    .filter(container -> "run-0".equals(container.getLabels().get(DockerSandboxExecutor.PHASE_LABEL)))
                    .toList();
            if (!matches.isEmpty()) return matches.getFirst();
            sleep(20);
        }
        return fail("Timed out waiting for a live student container");
    }

    private int activeSandboxContainers() {
        return (int) sandboxContainers().stream().filter(container ->
                container.getState() != null && "running".equalsIgnoreCase(container.getState())).count();
    }

    private List<Container> sandboxContainers() {
        return docker.listContainersCmd().withShowAll(true).exec().stream()
                .filter(container -> container.getLabels() != null)
                .filter(container -> "true".equals(container.getLabels().get(DockerSandboxExecutor.SANDBOX_LABEL)))
                .filter(container -> INSTANCE_ID.equals(container.getLabels().get(DockerSandboxExecutor.INSTANCE_LABEL)))
                .toList();
    }

    private List<String> sandboxVolumes() {
        var volumes = docker.listVolumesCmd().exec().getVolumes();
        if (volumes == null) return List.of();
        return volumes.stream()
                .filter(volume -> volume.getLabels() != null)
                .filter(volume -> INSTANCE_ID.equals(volume.getLabels().get(DockerSandboxExecutor.INSTANCE_LABEL)))
                .map(volume -> volume.getName())
                .toList();
    }

    private void awaitNoSandboxResources() {
        long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        while (System.nanoTime() < deadline) {
            if (sandboxContainers().isEmpty() && sandboxVolumes().isEmpty()) return;
            sleep(50);
        }
        assertThat(sandboxContainers()).as("residual student containers").isEmpty();
        assertThat(sandboxVolumes()).as("residual sandbox volumes").isEmpty();
    }

    private void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}

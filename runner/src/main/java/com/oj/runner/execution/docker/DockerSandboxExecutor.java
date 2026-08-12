package com.oj.runner.execution.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.Volume;
import com.oj.runner.api.RunnerCaseResult;
import com.oj.runner.api.RunnerCompileResult;
import com.oj.runner.api.RunnerJobResponse;
import com.oj.runner.api.RunnerLanguage;
import com.oj.runner.api.RunnerStatus;
import com.oj.runner.config.DockerSandboxProperties;
import com.oj.runner.execution.RunnerResponses;
import com.oj.runner.execution.SandboxExecutor;
import com.oj.runner.language.RunnerJob;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Trusted Docker control-plane executor. Student code only runs in disposable,
 * non-root containers and never in the Runner JVM.
 */
@Component
@ConditionalOnProperty(prefix = "runner.sandbox", name = "mode", havingValue = "docker")
public class DockerSandboxExecutor implements SandboxExecutor {

    static final String SANDBOX_LABEL = "com.practice-platform.sandbox";
    static final String JOB_LABEL = "com.practice-platform.job-id";
    static final String INSTANCE_LABEL = "com.practice-platform.runner-instance";
    static final String LANGUAGE_LABEL = "com.practice-platform.language";
    static final String PHASE_LABEL = "com.practice-platform.phase";
    static final String ARTIFACT_VOLUME_LABEL = "com.practice-platform.artifact-volume";
    private static final Logger log = LoggerFactory.getLogger(DockerSandboxExecutor.class);
    private static final String SANDBOX_USER = "10001:10001";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DockerClient docker;
    private final DockerSandboxProperties properties;
    private volatile boolean available;

    public DockerSandboxExecutor(DockerClient docker, DockerSandboxProperties properties) {
        this.docker = docker;
        this.properties = properties;
    }

    @PostConstruct
    void initialize() {
        validateInstanceId();
        try {
            docker.pingCmd().exec();
            propertiesImages().forEach(image -> docker.inspectImageCmd(image).exec());
            cleanupStaleSandboxes();
            cleanupStaleArtifactVolumes();
            available = true;
        } catch (RuntimeException exception) {
            available = false;
            log.warn("Docker sandbox unavailable type={} message={}",
                    exception.getClass().getSimpleName(), safeMessage(exception));
        }
    }

    @Override
    public RunnerJobResponse execute(RunnerJob job) {
        if (!available) {
            return RunnerResponses.systemError(job.request().requestId(), "Docker sandbox unavailable");
        }
        try {
            return executeJob(job);
        } catch (RuntimeException exception) {
            log.warn("Docker sandbox failed requestId={} type={} message={}",
                    job.request().requestId(), exception.getClass().getSimpleName(), safeMessage(exception));
            return RunnerResponses.systemError(job.request().requestId(), "Docker sandbox failed");
        }
    }

    @Override
    public boolean available() {
        return available;
    }

    private RunnerJobResponse executeJob(RunnerJob job) {
        RunnerLanguage language = job.request().language();
        String sourceFilename = job.profile().sourceFilename();
        byte[] sourceArchive = SandboxArtifactArchive.source(sourceFilename, job.request().sourceCode());
        String artifactVolume = createSandboxVolume(job, "artifacts");
        try {
            stageSource(job, artifactVolume, sourceArchive);
            ExecutionOutcome compilation;
            String compileContainer = createExecutionContainer(job, "compile", artifactVolume, null, true);
            try {
                compilation = executeCommand(
                        compileContainer,
                        DockerLanguageCommands.compile(language, job.request().limits().memoryMb()),
                        new byte[0],
                        job.request().limits().compileTimeMs(),
                        job.request().limits().outputLimitBytes());
            } finally {
                removeContainerFailClosed(compileContainer);
            }
            RunnerStatus compileStatus = compileStatus(compilation);
            RunnerCompileResult compileResult = new RunnerCompileResult(
                    compileStatus, compilation.exitCode(), compilation.combinedOutput(), compilation.timeMs(),
                    compileStatus == RunnerStatus.OK ? "" : "Compilation failed");
            if (compileStatus != RunnerStatus.OK) {
                return new RunnerJobResponse(job.request().requestId(), compileResult, List.of(), compileResult.message());
            }

            List<RunnerCaseResult> results = new ArrayList<>();
            for (int index = 0; index < job.request().cases().size(); index++) {
                var testCase = job.request().cases().get(index);
                String inputVolume = createSandboxVolume(job, "input-" + index);
                try {
                    stageSource(job, inputVolume,
                            SandboxArtifactArchive.stdin(testCase.stdin().getBytes(StandardCharsets.UTF_8)));
                    String runContainer = createExecutionContainer(
                            job, "run-" + index, artifactVolume, inputVolume, false);
                    ExecutionOutcome outcome;
                    try {
                        outcome = executeCommand(
                                runContainer,
                                DockerLanguageCommands.run(language, job.request().limits().memoryMb()),
                                new byte[0],
                                job.request().limits().runTimeMs(),
                                job.request().limits().outputLimitBytes());
                    } finally {
                        removeContainerFailClosed(runContainer);
                    }
                    RunnerStatus status = runStatus(outcome);
                    results.add(new RunnerCaseResult(
                            testCase.caseId(), status, outcome.exitCode(), outcome.stdout(), outcome.stderr(),
                            outcome.timeMs(), 0, status == RunnerStatus.OK ? "" : statusMessage(status)));
                    if (status != RunnerStatus.OK) {
                        break;
                    }
                } finally {
                    removeVolumeFailClosed(inputVolume);
                }
            }
            return new RunnerJobResponse(job.request().requestId(), compileResult, List.copyOf(results), "");
        } finally {
            removeVolumeFailClosed(artifactVolume);
        }
    }

    private String createExecutionContainer(
            RunnerJob job,
            String phase,
            String artifactVolume,
            String inputVolume,
            boolean compile) {
        String name = containerName(job.request().requestId(), phase);
        long memoryBytes = Math.multiplyExact(job.request().limits().memoryMb(), 1024L * 1024L);
        List<Bind> binds = new ArrayList<>();
        binds.add(new Bind(
                artifactVolume,
                new Volume(compile ? "/workspace" : "/artifacts"),
                compile ? AccessMode.rw : AccessMode.ro));
        if (!compile) {
            binds.add(new Bind(inputVolume, new Volume("/input"), AccessMode.ro));
        }
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withNetworkMode("none")
                .withIpcMode("private")
                .withReadonlyRootfs(true)
                .withPrivileged(false)
                .withCapDrop(Capability.values())
                .withMemory(memoryBytes)
                .withMemorySwap(memoryBytes)
                .withNanoCPUs(properties.getNanoCpus())
                .withPidsLimit(properties.getPidsLimit())
                .withSecurityOpts(List.of("no-new-privileges=true"))
                .withBinds(binds)
                .withTmpFs(compile
                        ? Map.of("/tmp", tmpfs(properties.getTmpBytes(), false))
                        : Map.of(
                                "/workspace", tmpfs(properties.getWorkspaceBytes(), true),
                                "/tmp", tmpfs(properties.getTmpBytes(), false)));
        var created = docker.createContainerCmd(properties.imageFor(job.request().language()))
                .withName(name)
                .withUser(SANDBOX_USER)
                .withWorkingDir("/workspace")
                .withEnv(environment(job.request().language()))
                .withLabels(sandboxLabels(job, phase))
                .withEntrypoint("/usr/bin/tail")
                .withCmd("-f", "/dev/null")
                .withHostConfig(hostConfig)
                .exec();
        try {
            docker.startContainerCmd(created.getId()).exec();
            return created.getId();
        } catch (RuntimeException exception) {
            removeContainerBestEffort(created.getId());
            throw exception;
        }
    }

    private String createSandboxVolume(RunnerJob job, String phase) {
        String volumeName = containerName(job.request().requestId(), phase);
        Map<String, String> labels = sandboxLabels(job, phase);
        labels.put(ARTIFACT_VOLUME_LABEL, "true");
        docker.createVolumeCmd().withName(volumeName).withLabels(labels).exec();
        return volumeName;
    }

    private void stageSource(RunnerJob job, String artifactVolume, byte[] archive) {
        String stagingContainer = createStagingContainer(job, artifactVolume);
        try (InputStream input = new ByteArrayInputStream(archive)) {
            docker.copyArchiveToContainerCmd(stagingContainer)
                    .withRemotePath("/workspace")
                    .withTarInputStream(input)
                    .exec();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not close sandbox source archive", exception);
        } finally {
            removeContainerFailClosed(stagingContainer);
        }
    }

    private String createStagingContainer(RunnerJob job, String artifactVolume) {
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withNetworkMode("none")
                .withIpcMode("private")
                .withReadonlyRootfs(false)
                .withPrivileged(false)
                .withCapDrop(Capability.values())
                .withMemory(64L * 1024 * 1024)
                .withMemorySwap(64L * 1024 * 1024)
                .withNanoCPUs(properties.getNanoCpus())
                .withPidsLimit(16L)
                .withSecurityOpts(List.of("no-new-privileges=true"))
                .withBinds(new Bind(artifactVolume, new Volume("/workspace"), AccessMode.rw));
        String name = containerName(job.request().requestId(), "stage");
        var created = docker.createContainerCmd(properties.imageFor(job.request().language()))
                .withName(name)
                .withUser(SANDBOX_USER)
                .withWorkingDir("/workspace")
                .withEnv(environment(job.request().language()))
                .withLabels(sandboxLabels(job, "stage"))
                .withEntrypoint("/usr/bin/tail")
                .withCmd("-f", "/dev/null")
                .withHostConfig(hostConfig)
                .exec();
        try {
            docker.startContainerCmd(created.getId()).exec();
            return created.getId();
        } catch (RuntimeException exception) {
            removeContainerBestEffort(created.getId());
            throw exception;
        }
    }

    private ExecutionOutcome executeCommand(
            String containerId,
            List<String> command,
            byte[] stdin,
            long timeoutMs,
            int outputLimitBytes) {
        var created = docker.execCreateCmd(containerId)
                .withAttachStdin(stdin.length > 0)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(false)
                .withUser(SANDBOX_USER)
                .withWorkingDir("/workspace")
                .withCmd(command.toArray(String[]::new))
                .exec();
        long started = System.nanoTime();
        AtomicBoolean outputExceeded = new AtomicBoolean();
        BoundedFrameCallback callback = new BoundedFrameCallback(outputLimitBytes, () -> {
            if (outputExceeded.compareAndSet(false, true)) {
                CompletableFuture.runAsync(() -> killContainerBestEffort(containerId));
            }
        });
        boolean completed;
        try (callback) {
            var start = docker.execStartCmd(created.getId()).withDetach(false).withTty(false);
            if (stdin.length > 0) {
                start.withStdIn(new ByteArrayInputStream(stdin));
            }
            start.exec(callback);
            completed = callback.awaitCompletion(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            killContainerBestEffort(containerId);
            throw new IllegalStateException("Sandbox execution interrupted", exception);
        } catch (IOException exception) {
            killContainerBestEffort(containerId);
            throw new IllegalStateException("Sandbox output capture failed", exception);
        }
        boolean timedOut = !completed && !outputExceeded.get();
        if (!completed) {
            killContainerBestEffort(containerId);
        }
        int exitCode = -1;
        if (completed && !outputExceeded.get()) {
            Long value = docker.inspectExecCmd(created.getId()).exec().getExitCodeLong();
            exitCode = value == null ? -1 : Math.toIntExact(value);
        }
        boolean oomKilled = inspectOomKilled(containerId);
        return new ExecutionOutcome(
                exitCode, callback.stdout(), callback.stderr(), elapsedMs(started),
                timedOut, outputExceeded.get(), oomKilled);
    }

    private RunnerStatus compileStatus(ExecutionOutcome outcome) {
        if (outcome.outputExceeded()) return RunnerStatus.OUTPUT_LIMIT_EXCEEDED;
        if (outcome.timedOut()) return RunnerStatus.TIME_LIMIT_EXCEEDED;
        if (outcome.oomKilled()) return RunnerStatus.MEMORY_LIMIT_EXCEEDED;
        return outcome.exitCode() == 0 ? RunnerStatus.OK : RunnerStatus.COMPILE_ERROR;
    }

    private RunnerStatus runStatus(ExecutionOutcome outcome) {
        if (outcome.outputExceeded()) return RunnerStatus.OUTPUT_LIMIT_EXCEEDED;
        if (outcome.timedOut()) return RunnerStatus.TIME_LIMIT_EXCEEDED;
        if (outcome.oomKilled()) return RunnerStatus.MEMORY_LIMIT_EXCEEDED;
        return outcome.exitCode() == 0 ? RunnerStatus.OK : RunnerStatus.RUNTIME_ERROR;
    }

    private boolean inspectOomKilled(String containerId) {
        try {
            Boolean oomKilled = docker.inspectContainerCmd(containerId).exec().getState().getOOMKilled();
            return Boolean.TRUE.equals(oomKilled);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void cleanupStaleSandboxes() {
        docker.listContainersCmd().withShowAll(true).exec().stream()
                .filter(container -> "true".equals(container.getLabels().get(SANDBOX_LABEL)))
                .filter(container -> properties.getInstanceId().equals(container.getLabels().get(INSTANCE_LABEL)))
                .forEach(container -> removeContainerFailClosed(container.getId()));
    }

    private void cleanupStaleArtifactVolumes() {
        var volumes = docker.listVolumesCmd()
                .withFilter("label", List.of(
                        ARTIFACT_VOLUME_LABEL + "=true",
                        INSTANCE_LABEL + "=" + properties.getInstanceId()))
                .exec()
                .getVolumes();
        if (volumes == null) {
            return;
        }
        volumes.stream()
                .filter(volume -> volume.getLabels() != null)
                .filter(volume -> "true".equals(volume.getLabels().get(ARTIFACT_VOLUME_LABEL)))
                .filter(volume -> properties.getInstanceId().equals(volume.getLabels().get(INSTANCE_LABEL)))
                .forEach(volume -> removeVolumeFailClosed(volume.getName()));
    }

    private void removeContainerFailClosed(String containerId) {
        RuntimeException failure = null;
        for (int attempt = 1; attempt <= properties.getCleanupRetries(); attempt++) {
            try {
                docker.removeContainerCmd(containerId).withForce(true).withRemoveVolumes(true).exec();
                return;
            } catch (NotFoundException ignored) {
                return;
            } catch (RuntimeException exception) {
                failure = exception;
            }
        }
        throw new IllegalStateException("Sandbox container cleanup failed", failure);
    }

    private void removeContainerBestEffort(String containerId) {
        try {
            docker.removeContainerCmd(containerId).withForce(true).withRemoveVolumes(true).exec();
        } catch (RuntimeException ignored) {
            log.warn("Sandbox container best-effort cleanup failed containerId={}", containerId);
        }
    }

    private void removeVolumeFailClosed(String volumeName) {
        RuntimeException failure = null;
        for (int attempt = 1; attempt <= properties.getCleanupRetries(); attempt++) {
            try {
                docker.removeVolumeCmd(volumeName).exec();
                return;
            } catch (NotFoundException ignored) {
                return;
            } catch (RuntimeException exception) {
                failure = exception;
            }
        }
        throw new IllegalStateException("Sandbox artifact volume cleanup failed", failure);
    }

    private void killContainerBestEffort(String containerId) {
        try {
            docker.killContainerCmd(containerId).exec();
        } catch (RuntimeException ignored) {
            // The process may already have exited or the container may have been removed.
        }
    }

    private List<String> propertiesImages() {
        return List.of(
                properties.getPythonImage(), properties.getJavascriptImage(), properties.getCImage(),
                properties.getCppImage(), properties.getJavaImage()).stream().distinct().toList();
    }

    private Map<String, String> sandboxLabels(RunnerJob job, String phase) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(SANDBOX_LABEL, "true");
        labels.put(JOB_LABEL, job.request().requestId());
        labels.put(INSTANCE_LABEL, properties.getInstanceId());
        labels.put(LANGUAGE_LABEL, job.request().language().name());
        labels.put(PHASE_LABEL, phase);
        return labels;
    }

    private List<String> environment(RunnerLanguage language) {
        List<String> environment = new ArrayList<>(List.of(
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                "LANG=C.UTF-8", "LC_ALL=C.UTF-8", "HOME=/workspace"));
        if (language == RunnerLanguage.JAVA) {
            environment.add("JAVA_HOME=/opt/java/openjdk");
        }
        return List.copyOf(environment);
    }

    private String tmpfs(long size, boolean executable) {
        return "rw,nosuid,nodev," + (executable ? "exec" : "noexec")
                + ",size=" + size + ",mode=0700,uid=10001,gid=10001";
    }

    private String containerName(String requestId, String phase) {
        String trustedId = requestId.replace("-", "").substring(0, 16);
        byte[] random = new byte[6];
        RANDOM.nextBytes(random);
        return "oj-sandbox-" + trustedId + "-" + java.util.HexFormat.of().formatHex(random) + "-" + phase;
    }

    private void validateInstanceId() {
        if (!properties.getInstanceId().matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalStateException("Runner Docker instance id is invalid");
        }
    }

    private String statusMessage(RunnerStatus status) {
        return switch (status) {
            case TIME_LIMIT_EXCEEDED -> "Execution timed out";
            case MEMORY_LIMIT_EXCEEDED -> "Memory limit exceeded";
            case OUTPUT_LIMIT_EXCEEDED -> "Output limit exceeded";
            case RUNTIME_ERROR -> "Runtime error";
            default -> "Sandbox execution failed";
        };
    }

    private long elapsedMs(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null) return "";
        return message.replace('\r', ' ').replace('\n', ' ').substring(0, Math.min(message.length(), 512));
    }

    private record ExecutionOutcome(
            int exitCode,
            String stdout,
            String stderr,
            long timeMs,
            boolean timedOut,
            boolean outputExceeded,
            boolean oomKilled) {

        String combinedOutput() {
            if (stdout.isBlank()) return stderr;
            if (stderr.isBlank()) return stdout;
            return stdout + System.lineSeparator() + stderr;
        }
    }

    private static final class BoundedFrameCallback extends ResultCallback.Adapter<Frame> {
        private final int maxBytes;
        private final Runnable overflowAction;
        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        private int captured;
        private boolean overflow;

        private BoundedFrameCallback(int maxBytes, Runnable overflowAction) {
            this.maxBytes = maxBytes;
            this.overflowAction = overflowAction;
        }

        @Override
        public synchronized void onNext(Frame frame) {
            byte[] payload = frame.getPayload();
            if (overflow || payload == null) return;
            int remaining = maxBytes - captured;
            int accepted = Math.max(0, Math.min(remaining, payload.length));
            ByteArrayOutputStream target = frame.getStreamType() == StreamType.STDERR ? stderr : stdout;
            target.write(payload, 0, accepted);
            captured += accepted;
            if (accepted < payload.length) {
                overflow = true;
                overflowAction.run();
            }
        }

        synchronized String stdout() {
            return stdout.toString(StandardCharsets.UTF_8);
        }

        synchronized String stderr() {
            return stderr.toString(StandardCharsets.UTF_8);
        }
    }
}

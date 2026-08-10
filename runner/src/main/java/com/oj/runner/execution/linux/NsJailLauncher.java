package com.oj.runner.execution.linux;

import com.oj.runner.config.LinuxSandboxProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The only production component permitted to start an operating-system process.
 * It starts nsjail with structured arguments; it never starts student commands directly.
 */
@Component
@Profile("!runner-contract-test")
@ConditionalOnProperty(prefix = "runner.sandbox", name = "mode", havingValue = "linux")
public class NsJailLauncher implements SandboxProcessLauncher {

    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_DIAGNOSTIC_BYTES = 65_536;

    private final LinuxSandboxProperties properties;
    private final Path nsjailPath;
    private final Path cgroupRoot;

    public NsJailLauncher(LinuxSandboxProperties properties) {
        this.properties = properties;
        this.nsjailPath = Path.of(properties.getNsjailPath()).toAbsolutePath().normalize();
        this.cgroupRoot = Path.of(properties.getCgroupV2Mount()).toAbsolutePath().normalize();
    }

    public static boolean probeVersion(Path nsjail) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(nsjail.toString(), "--version");
            builder.environment().clear();
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            process = builder.start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                terminateTree(process);
            }
        }
    }

    @Override
    public NsJailExecutionResult launch(NsJailInvocation invocation) {
        long startedAt = System.nanoTime();
        AtomicLong capturedBytes = new AtomicLong();
        AtomicBoolean outputExceeded = new AtomicBoolean();
        AtomicLong peakMemoryBytes = new AtomicLong();
        AtomicBoolean oomKilled = new AtomicBoolean();
        AtomicReference<Path> ownCgroup = new AtomicReference<>();
        Process process = null;

        try (ExecutorService io = Executors.newVirtualThreadPerTaskExecutor()) {
            List<String> command = new ArrayList<>();
            command.add(nsjailPath.toString());
            command.add("--config");
            command.add(invocation.config().toString());
            command.add("--");
            command.addAll(invocation.argv());

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().clear();
            builder.directory(invocation.workspace().toFile());
            process = builder.start();
            Process runningProcess = process;

            LimitedCapture stdout = new LimitedCapture(
                    runningProcess.getInputStream(), capturedBytes, outputExceeded, invocation.outputLimitBytes());
            LimitedCapture stderr = new LimitedCapture(
                    runningProcess.getErrorStream(), capturedBytes, outputExceeded, invocation.outputLimitBytes());
            Future<byte[]> stdoutFuture = io.submit(stdout);
            Future<byte[]> stderrFuture = io.submit(stderr);
            Future<?> stdinFuture = io.submit(() -> writeInput(runningProcess.getOutputStream(), invocation.stdin()));

            // The outer watchdog enforces the requested wall limit exactly. The grace period is
            // reserved for process-tree termination; it is never extra execution time.
            long deadline = System.nanoTime() + Duration.ofMillis(invocation.wallTimeMs()).toNanos();
            SandboxTermination forced = null;
            while (process.isAlive()) {
                locateOwnCgroup(process).ifPresent(path -> ownCgroup.compareAndSet(null, path));
                Path cgroup = ownCgroup.get();
                if (cgroup != null) {
                    sampleCgroup(cgroup, peakMemoryBytes, oomKilled);
                }
                if (outputExceeded.get()) {
                    forced = SandboxTermination.OUTPUT_LIMIT;
                    break;
                }
                WorkspaceUsage workspaceUsage = workspaceUsage(invocation.workspace());
                if (workspaceUsage.exceeded()) {
                    forced = SandboxTermination.WORKSPACE_LIMIT;
                    break;
                }
                if (System.nanoTime() >= deadline) {
                    forced = SandboxTermination.TIME_LIMIT;
                    break;
                }
                process.waitFor(properties.getWorkspacePollMs(), TimeUnit.MILLISECONDS);
            }
            if (forced != null && process.isAlive()) {
                terminateTree(process);
            }
            process.waitFor(properties.getOuterTimeoutGraceMs() + 1000, TimeUnit.MILLISECONDS);
            if (process.isAlive()) {
                terminateTree(process);
                return NsJailExecutionResult.sandboxError("nsjail process tree did not terminate");
            }

            Path cgroup = ownCgroup.get();
            if (cgroup != null) {
                sampleCgroup(cgroup, peakMemoryBytes, oomKilled);
            }
            await(stdinFuture);
            byte[] stdoutBytes = await(stdoutFuture);
            byte[] stderrBytes = await(stderrFuture);
            if (cgroup != null && !cleanupOwnCgroup(cgroup)) {
                return NsJailExecutionResult.sandboxError("nsjail cgroup cleanup failed");
            }

            long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            int exitCode = process.exitValue();
            SandboxTermination termination = forced;
            if (termination == null && outputExceeded.get()) {
                termination = SandboxTermination.OUTPUT_LIMIT;
            }
            long memoryLimitBytes = invocation.memoryLimitMb() * 1024L * 1024L;
            boolean reachedMemoryLimit = peakMemoryBytes.get() >= memoryLimitBytes * 95 / 100;
            if (termination == null && (oomKilled.get() || (exitCode == 137 && reachedMemoryLimit))) {
                termination = SandboxTermination.MEMORY_LIMIT;
            }
            if (termination == null) {
                termination = SandboxTermination.COMPLETED;
            }
            return new NsJailExecutionResult(
                    termination,
                    exitCode,
                    new String(stdoutBytes, StandardCharsets.UTF_8),
                    new String(stderrBytes, StandardCharsets.UTF_8),
                    elapsedMs,
                    peakMemoryBytes.get() / 1024,
                    readDiagnostic(invocation.log()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) {
                terminateTree(process);
            }
            return NsJailExecutionResult.sandboxError("Sandbox execution interrupted");
        } catch (IOException | ExecutionException | ArithmeticException exception) {
            if (process != null) {
                terminateTree(process);
            }
            return NsJailExecutionResult.sandboxError("nsjail launch failed");
        }
    }

    private static void writeInput(OutputStream destination, byte[] input) {
        try (destination) {
            destination.write(input);
        } catch (IOException ignored) {
            // A sandbox may exit before consuming all input. Its exit status remains authoritative.
        }
    }

    private void sampleCgroup(Path cgroup, AtomicLong peakMemory, AtomicBoolean oomKilled) {
        if (!Files.isDirectory(cgroup, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(cgroup)) {
            return;
        }
        readLong(cgroup.resolve("memory.peak")).ifPresent(value -> peakMemory.accumulateAndGet(value, Math::max));
        try {
            Files.readAllLines(cgroup.resolve("memory.events")).stream()
                    .filter(line -> line.startsWith("oom_kill "))
                    .map(line -> line.substring("oom_kill ".length()).trim())
                    .mapToLong(Long::parseLong)
                    .filter(value -> value > 0)
                    .findFirst()
                    .ifPresent(value -> oomKilled.set(true));
        } catch (IOException | NumberFormatException ignored) {
            // A short-lived cgroup can disappear between observations.
        }
    }

    private java.util.Optional<Path> locateOwnCgroup(Process process) {
        return process.toHandle().descendants()
                .map(ProcessHandle::pid)
                .map(pid -> cgroupRoot.resolve("NSJAIL." + pid).normalize())
                .filter(path -> path.getParent().equals(cgroupRoot))
                .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                .findFirst();
    }

    private WorkspaceUsage workspaceUsage(Path workspace) throws IOException {
        AtomicLong bytes = new AtomicLong();
        AtomicLong files = new AtomicLong();
        AtomicBoolean excessiveFile = new AtomicBoolean();
        try (var entries = Files.walk(workspace)) {
            entries.forEach(path -> {
                try {
                    BasicFileAttributes attributes = Files.readAttributes(
                            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (attributes.isRegularFile()) {
                        files.incrementAndGet();
                        bytes.addAndGet(attributes.size());
                        excessiveFile.compareAndSet(false, attributes.size() > properties.getMaxFileBytes());
                    } else if (attributes.isSymbolicLink()) {
                        files.incrementAndGet();
                    }
                } catch (IOException exception) {
                    throw new WorkspaceInspectionException(exception);
                }
            });
        } catch (WorkspaceInspectionException exception) {
            throw exception.ioException;
        }
        return new WorkspaceUsage(
                bytes.get() > properties.getWorkspaceBytes()
                        || files.get() > properties.getWorkspaceFiles()
                        || excessiveFile.get());
    }

    private boolean cleanupOwnCgroup(Path cgroup) {
        try {
            if (!Files.exists(cgroup, LinkOption.NOFOLLOW_LINKS)) {
                return true;
            }
            if (Files.isSymbolicLink(cgroup) || !cgroup.getParent().equals(cgroupRoot)) {
                return false;
            }
            String procs = Files.readString(cgroup.resolve("cgroup.procs"));
            if (!procs.isBlank()) {
                return false;
            }
            Files.delete(cgroup);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private String readDiagnostic(Path log) {
        try (InputStream input = Files.newInputStream(log)) {
            return new String(input.readNBytes(MAX_DIAGNOSTIC_BYTES), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "";
        }
    }

    private java.util.OptionalLong readLong(Path path) {
        try {
            return java.util.OptionalLong.of(Long.parseLong(Files.readString(path).trim()));
        } catch (IOException | NumberFormatException exception) {
            return java.util.OptionalLong.empty();
        }
    }

    private static <T> T await(Future<T> future) throws InterruptedException, ExecutionException {
        return future.get();
    }

    private static void terminateTree(Process process) {
        ProcessHandle root = process.toHandle();
        List<ProcessHandle> descendants = root.descendants()
                .sorted(Comparator.comparingInt(NsJailLauncher::depth).reversed())
                .toList();
        descendants.forEach(ProcessHandle::destroy);
        root.destroy();
        waitBriefly(root);
        descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        if (root.isAlive()) {
            root.destroyForcibly();
        }
    }

    private static int depth(ProcessHandle process) {
        int depth = 0;
        java.util.Optional<ProcessHandle> parent = process.parent();
        while (parent.isPresent() && depth < 64) {
            depth++;
            parent = parent.get().parent();
        }
        return depth;
    }

    private static void waitBriefly(ProcessHandle process) {
        try {
            process.onExit().get(250, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // Forced termination follows immediately.
        }
    }

    private record WorkspaceUsage(boolean exceeded) {
    }

    private static final class WorkspaceInspectionException extends RuntimeException {
        private final IOException ioException;

        private WorkspaceInspectionException(IOException ioException) {
            super(ioException);
            this.ioException = ioException;
        }
    }

    private static final class LimitedCapture implements java.util.concurrent.Callable<byte[]> {
        private final InputStream input;
        private final AtomicLong total;
        private final AtomicBoolean exceeded;
        private final long limit;

        private LimitedCapture(InputStream input, AtomicLong total, AtomicBoolean exceeded, long limit) {
            this.input = input;
            this.total = total;
            this.exceeded = exceeded;
            this.limit = limit;
        }

        @Override
        public byte[] call() throws IOException {
            try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    long before = total.getAndAdd(read);
                    long allowed = Math.max(0, Math.min(read, limit - before));
                    if (allowed > 0) {
                        output.write(buffer, 0, (int) allowed);
                    }
                    if (before + read > limit) {
                        exceeded.set(true);
                    }
                }
                return output.toByteArray();
            }
        }
    }
}

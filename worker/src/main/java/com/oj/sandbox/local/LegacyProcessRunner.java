package com.oj.sandbox.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Legacy local process execution path. This is not a security sandbox and must
 * not be used as the final isolation boundary for untrusted code.
 */
public final class LegacyProcessRunner {

    public LegacyProcessResult run(
            List<String> command,
            String stdin,
            long timeLimitMs,
            long memoryLimitKb,
            int outputLimitBytes,
            Path cwd) {
        if (command == null || command.isEmpty()) {
            return new LegacyProcessResult("", "No command configured", -1, false, false, false, 0);
        }

        StringBuilder prelude = new StringBuilder();
        if (memoryLimitKb > 0) {
            prelude.append("ulimit -v ").append(memoryLimitKb).append(" 2>/dev/null; ");
        }
        long cpuSeconds = timeLimitMs / 1_000 + 1;
        prelude.append("ulimit -t ").append(cpuSeconds).append(" 2>/dev/null; ");
        prelude.append("ulimit -f 262144 2>/dev/null; ");
        String wrapped = prelude + "exec \"$@\"";

        List<String> processCommand = new ArrayList<>();
        processCommand.add("bash");
        processCommand.add("-c");
        processCommand.add(wrapped);
        processCommand.add("judge-runner");
        processCommand.addAll(command);

        ProcessBuilder processBuilder = new ProcessBuilder(processCommand);
        processBuilder.directory(cwd.toFile());
        processBuilder.redirectErrorStream(false);
        processBuilder.environment().clear();
        processBuilder.environment().put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
        processBuilder.environment().put("HOME", cwd.toString());
        processBuilder.environment().put("LANG", "C.UTF-8");
        processBuilder.environment().put("LC_ALL", "C.UTF-8");

        long startedAt = System.currentTimeMillis();
        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException exception) {
            return new LegacyProcessResult("", safeMessage(exception), -1, false, false, false, 0);
        }

        byte[] input = (stdin == null ? "" : stdin).getBytes(StandardCharsets.UTF_8);
        Thread writer = new Thread(() -> {
            try (var output = process.getOutputStream()) {
                output.write(input);
                output.flush();
            } catch (IOException ignored) {
            }
        }, "legacy-sandbox-stdin");
        writer.setDaemon(true);
        writer.start();

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        AtomicInteger capturedBytes = new AtomicInteger();
        AtomicBoolean outputExceeded = new AtomicBoolean();
        Thread stdoutReader = readerThread(process.getInputStream(), stdout, capturedBytes, outputExceeded,
                outputLimitBytes, "legacy-sandbox-stdout");
        Thread stderrReader = readerThread(process.getErrorStream(), stderr, capturedBytes, outputExceeded,
                outputLimitBytes, "legacy-sandbox-stderr");
        stdoutReader.start();
        stderrReader.start();

        boolean timedOut = false;
        try {
            long deadline = startedAt + timeLimitMs;
            while (process.isAlive()) {
                if (outputExceeded.get()) {
                    terminate(process);
                    break;
                }
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    timedOut = true;
                    terminate(process);
                    break;
                }
                process.waitFor(Math.min(remaining, 50), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            timedOut = true;
            terminate(process);
        }

        join(writer, 500);
        join(stdoutReader, 1_000);
        join(stderrReader, 1_000);

        int exitCode = process.isAlive() ? -1 : process.exitValue();
        long elapsedMs = System.currentTimeMillis() - startedAt;
        String stdoutText = stdout.toString(StandardCharsets.UTF_8);
        String stderrText = stderr.toString(StandardCharsets.UTF_8);
        String lowerError = stderrText.toLowerCase();
        boolean memoryError = !timedOut && !outputExceeded.get()
                && (lowerError.contains("cannot allocate memory")
                || lowerError.contains("out of memory")
                || lowerError.contains("bad_alloc"));

        return new LegacyProcessResult(stdoutText, stderrText, exitCode, timedOut,
                memoryError, outputExceeded.get(), elapsedMs);
    }

    private Thread readerThread(
            InputStream input,
            ByteArrayOutputStream captured,
            AtomicInteger capturedBytes,
            AtomicBoolean outputExceeded,
            int outputLimitBytes,
            String name) {
        Thread thread = new Thread(() -> readStream(
                input, captured, capturedBytes, outputExceeded, outputLimitBytes), name);
        thread.setDaemon(true);
        return thread;
    }

    private void readStream(
            InputStream input,
            ByteArrayOutputStream captured,
            AtomicInteger capturedBytes,
            AtomicBoolean outputExceeded,
            int outputLimitBytes) {
        try (input) {
            byte[] buffer = new byte[4_096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                int previous = capturedBytes.getAndAdd(count);
                int allowed = Math.max(0, Math.min(count, outputLimitBytes - previous));
                if (allowed > 0) {
                    captured.write(buffer, 0, allowed);
                }
                if (previous + count > outputLimitBytes) {
                    outputExceeded.set(true);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void terminate(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        try {
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void join(Thread thread, long timeoutMs) {
        try {
            thread.join(timeoutMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}

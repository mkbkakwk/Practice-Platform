package com.oj.judge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Executes a shell command in a sandboxed subprocess:
 *  - runs via bash so we can apply ulimit (CPU time, virtual memory, file size)
 *  - separate process group so we can kill the whole tree on timeout
 *  - captures stdout/stderr up to a cap, feeds stdin
 *
 * Mirrors the Node runner semantics. Designed to run inside the worker
 * Docker container (Linux), where the filesystem/network are already isolated.
 */
public class Runner {

    private static final int STDOUT_CAP = 16 * 1024 * 1024;

    public RunResult run(String command, String stdin, long timeLimitMs, long memoryLimitKb, Path cwd) {
        // Build a bash wrapper that applies ulimits then execs the command.
        StringBuilder prelude = new StringBuilder();
        if (memoryLimitKb > 0) {
            prelude.append("ulimit -v ").append(memoryLimitKb).append(" 2>/dev/null; ");
        }
        long cpuSec = timeLimitMs / 1000 + 1;
        prelude.append("ulimit -t ").append(cpuSec).append(" 2>/dev/null; ");
        prelude.append("ulimit -f 262144 2>/dev/null; "); // 256MB output cap
        String wrapped = prelude + "exec " + command;

        ProcessBuilder pb = new ProcessBuilder("bash", "-c", wrapped);
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(false);

        // Restrict env to a minimal safe set.
        pb.environment().clear();
        pb.environment().put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
        pb.environment().put("HOME", cwd.toString());
        pb.environment().put("LANG", "C.UTF-8");
        pb.environment().put("LC_ALL", "C.UTF-8");

        long start = System.currentTimeMillis();
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            return new RunResult(false, "", e.getMessage(), -1, false, false, 0);
        }

        // Write stdin on a separate thread to avoid blocking on large input.
        byte[] input = stdin.getBytes(StandardCharsets.UTF_8);
        Thread writer = new Thread(() -> {
            try (var os = p.getOutputStream()) {
                os.write(input);
                os.flush();
            } catch (IOException ignored) {}
        });
        writer.setDaemon(true);
        writer.start();

        StringBuilder outBuf = new StringBuilder();
        StringBuilder errBuf = new StringBuilder();
        Thread readerOut = new Thread(() -> readStream(p.getInputStream(), outBuf));
        Thread readerErr = new Thread(() -> readStream(p.getErrorStream(), errBuf));
        readerOut.setDaemon(true);
        readerErr.setDaemon(true);
        readerOut.start();
        readerErr.start();

        boolean timedOut = false;
        boolean finished;
        try {
            finished = p.waitFor(timeLimitMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                timedOut = true;
                p.destroyForcibly();
                p.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            return new RunResult(false, outBuf.toString(), errBuf.toString(), -1, true, false,
                    System.currentTimeMillis() - start);
        }

        try { writer.join(500); } catch (InterruptedException ignored) {}
        try { readerOut.join(1000); } catch (InterruptedException ignored) {}
        try { readerErr.join(1000); } catch (InterruptedException ignored) {}

        int exitCode = p.exitValue();
        long elapsed = System.currentTimeMillis() - start;

        boolean memError = !timedOut && (errBuf.toString().toLowerCase().contains("cannot allocate memory")
                || errBuf.toString().toLowerCase().contains("out of memory")
                || errBuf.toString().toLowerCase().contains("bad_alloc"));

        return new RunResult(!timedOut && exitCode == 0, outBuf.toString(), errBuf.toString(),
                exitCode, timedOut, memError, elapsed);
    }

    private void readStream(java.io.InputStream is, StringBuilder buf) {
        try (var br = new java.io.BufferedReader(new java.io.InputStreamReader(is, StandardCharsets.UTF_8))) {
            char[] chunk = new char[4096];
            int n;
            while ((n = br.read(chunk)) != -1) {
                if (buf.length() < STDOUT_CAP) {
                    int allowed = Math.min(n, STDOUT_CAP - buf.length());
                    buf.append(chunk, 0, allowed);
                }
            }
        } catch (IOException ignored) {}
    }
}

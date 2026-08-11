package com.oj.runner.execution.linux;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ExecutionCgroupLease implements AutoCloseable {

    private final ExecutionCgroupManager manager;
    private final Path path;
    private final AtomicBoolean closed = new AtomicBoolean();

    ExecutionCgroupLease(ExecutionCgroupManager manager, Path path) {
        this.manager = manager;
        this.path = path;
    }

    public Path path() {
        return path;
    }

    ExecutionCgroupSnapshot snapshot() throws IOException {
        if (closed.get()) {
            throw new IOException("execution cgroup lease is closed");
        }
        return manager.snapshot(path);
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            manager.release(path);
        }
    }
}

package com.oj.runner.execution.linux;

import com.oj.runner.config.LinuxSandboxProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;

@Component
public class SandboxWorkspaceManager {

    private final Path workspaceRoot;

    public SandboxWorkspaceManager(LinuxSandboxProperties properties) {
        workspaceRoot = Path.of(properties.getWorkspaceRoot()).toAbsolutePath().normalize();
    }

    public SandboxWorkspace create(String requestId) throws IOException {
        validateRoot();
        Path root = Files.createTempDirectory(workspaceRoot, "job-" + requestId + "-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Path files = Files.createDirectory(root.resolve("workspace"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Path metadata = Files.createDirectory(root.resolve("metadata"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Files.createDirectory(files.resolve("classes"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        return new SandboxWorkspace(root, files, metadata);
    }

    public void writeSource(SandboxWorkspace workspace, String filename, String source) throws IOException {
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new IllegalArgumentException("Sandbox source filename is invalid");
        }
        Path destination = workspace.files().resolve(filename).normalize();
        if (!destination.getParent().equals(workspace.files())) {
            throw new IllegalArgumentException("Sandbox source path escaped its workspace");
        }
        Files.writeString(destination, source, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(destination, PosixFilePermissions.fromString("rw-------"));
    }

    public void cleanup(SandboxWorkspace workspace) throws IOException {
        Path root = workspace.root().toAbsolutePath().normalize();
        if (!root.startsWith(workspaceRoot) || root.equals(workspaceRoot)) {
            throw new IOException("Refusing to clean an unsafe sandbox workspace path");
        }
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        // Never follow links created by untrusted code during recursive cleanup.
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new WorkspaceCleanupException(exception);
                }
            });
        } catch (WorkspaceCleanupException exception) {
            throw exception.ioException;
        }
    }

    private void validateRoot() throws IOException {
        if (!Files.isDirectory(workspaceRoot, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(workspaceRoot)) {
            throw new IOException("Sandbox workspace root is unavailable or unsafe");
        }
    }

    private static final class WorkspaceCleanupException extends RuntimeException {
        private final IOException ioException;

        private WorkspaceCleanupException(IOException ioException) {
            super(ioException);
            this.ioException = ioException;
        }
    }
}

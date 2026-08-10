package com.oj.runner.execution.linux;

import com.oj.runner.config.LinuxSandboxProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component("delegatedCgroupControllerInitializer")
@Profile("!runner-contract-test")
@ConditionalOnProperty(prefix = "runner.sandbox", name = "mode", havingValue = "linux")
public class DelegatedCgroupControllerInitializer {

    private static final List<String> REQUIRED_CONTROLLERS = List.of("cpu", "memory", "pids");

    private final LinuxSandboxProperties properties;
    private final CgroupFileAccess files;

    @Autowired
    public DelegatedCgroupControllerInitializer(LinuxSandboxProperties properties) {
        this(properties, new NioCgroupFileAccess());
    }

    DelegatedCgroupControllerInitializer(LinuxSandboxProperties properties, CgroupFileAccess files) {
        this.properties = properties;
        this.files = files;
    }

    @PostConstruct
    void initialize() {
        Path root = configuredRoot();
        Path controllersFile = child(root, "cgroup.controllers");
        Path subtreeControlFile = child(root, "cgroup.subtree_control");

        if (!files.isDirectory(root) || files.isSymbolicLink(root)) {
            throw failure("delegated cgroup root is unavailable");
        }
        if (!files.isRegularFile(controllersFile) || files.isSymbolicLink(controllersFile)) {
            throw failure("cgroup.controllers is unavailable");
        }
        if (!files.isRegularFile(subtreeControlFile) || files.isSymbolicLink(subtreeControlFile)) {
            throw failure("cgroup.subtree_control is unavailable");
        }

        Set<String> available = readWords(controllersFile, "cannot read cgroup.controllers");
        if (!available.containsAll(REQUIRED_CONTROLLERS)) {
            Set<String> missing = new LinkedHashSet<>(REQUIRED_CONTROLLERS);
            missing.removeAll(available);
            throw failure("required cgroup controllers are unavailable: " + String.join(",", missing));
        }
        if (!files.isWritable(subtreeControlFile)) {
            throw failure("cgroup.subtree_control is not writable by the delegated Runner service");
        }

        Set<String> enabled = readWords(subtreeControlFile, "cannot read cgroup.subtree_control");
        List<String> missing = REQUIRED_CONTROLLERS.stream()
                .filter(controller -> !enabled.contains(controller))
                .toList();
        if (!missing.isEmpty()) {
            String request = missing.stream()
                    .map(controller -> "+" + controller)
                    .collect(java.util.stream.Collectors.joining(" "));
            try {
                files.write(subtreeControlFile, request);
            } catch (IOException exception) {
                throw failure("cannot enable delegated cgroup controllers", exception);
            }
        }

        Set<String> verified = readWords(subtreeControlFile, "cannot verify cgroup.subtree_control");
        if (!verified.containsAll(REQUIRED_CONTROLLERS)) {
            throw failure("delegated cgroup controller enablement could not be verified");
        }
    }

    private Path configuredRoot() {
        String value = properties.getCgroupV2Mount();
        try {
            Path root = Path.of(value);
            if (!root.isAbsolute() || value.contains("\n") || value.contains("\r")) {
                throw failure("RUNNER_CGROUP_V2_MOUNT must be an absolute path");
            }
            return root.normalize();
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalStateException illegalStateException) {
                throw illegalStateException;
            }
            throw failure("RUNNER_CGROUP_V2_MOUNT is invalid", exception);
        }
    }

    private Path child(Path root, String filename) {
        Path child = root.resolve(filename).normalize();
        if (!child.startsWith(root) || !root.equals(child.getParent())) {
            throw failure("cgroup control path escaped the configured delegated root");
        }
        return child;
    }

    private Set<String> readWords(Path path, String error) {
        try {
            String value = files.read(path);
            if (value.isBlank()) {
                return Set.of();
            }
            return new LinkedHashSet<>(List.of(value.trim().split("\\s+")));
        } catch (IOException exception) {
            throw failure(error, exception);
        }
    }

    private IllegalStateException failure(String message) {
        return new IllegalStateException("Linux sandbox cgroup initialization failed: " + message);
    }

    private IllegalStateException failure(String message, Throwable cause) {
        return new IllegalStateException("Linux sandbox cgroup initialization failed: " + message, cause);
    }

    interface CgroupFileAccess {
        boolean isDirectory(Path path);

        boolean isRegularFile(Path path);

        boolean isSymbolicLink(Path path);

        boolean isWritable(Path path);

        String read(Path path) throws IOException;

        void write(Path path, String request) throws IOException;
    }

    private static final class NioCgroupFileAccess implements CgroupFileAccess {
        @Override
        public boolean isDirectory(Path path) {
            return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
        }

        @Override
        public boolean isRegularFile(Path path) {
            return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
        }

        @Override
        public boolean isSymbolicLink(Path path) {
            return Files.isSymbolicLink(path);
        }

        @Override
        public boolean isWritable(Path path) {
            return Files.isWritable(path);
        }

        @Override
        public String read(Path path) throws IOException {
            return Files.readString(path, StandardCharsets.UTF_8);
        }

        @Override
        public void write(Path path, String request) throws IOException {
            Files.writeString(path, request, StandardCharsets.UTF_8, StandardOpenOption.WRITE);
        }
    }
}

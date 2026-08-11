package com.oj.runner.execution.linux;

import com.oj.runner.config.LinuxSandboxProperties;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Component
@Profile("!runner-contract-test")
@ConditionalOnProperty(prefix = "runner.sandbox", name = "mode", havingValue = "linux")
public class ExecutionCgroupManager {

    private static final List<String> REQUIRED_CONTROLLERS = List.of("cpu", "memory", "pids");
    private static final Pattern TRUSTED_NAME = Pattern.compile("RUNNER\\.[0-9a-f]{32}");

    private final Path delegatedRoot;
    private final CgroupFileAccess files;
    private final Supplier<String> ids;

    @Autowired
    public ExecutionCgroupManager(LinuxSandboxProperties properties) {
        this(properties, new NioCgroupFileAccess(),
                () -> UUID.randomUUID().toString().replace("-", ""));
    }

    ExecutionCgroupManager(
            LinuxSandboxProperties properties,
            CgroupFileAccess files,
            Supplier<String> ids) {
        this.delegatedRoot = Path.of(properties.getCgroupV2Mount()).toAbsolutePath().normalize();
        this.files = files;
        this.ids = ids;
    }

    public ExecutionCgroupLease allocate() throws IOException {
        validateDelegatedRoot();
        String name = "RUNNER." + ids.get();
        if (!TRUSTED_NAME.matcher(name).matches()) {
            throw failure("generated execution cgroup name is invalid");
        }
        Path path = delegatedRoot.resolve(name).normalize();
        requireDirectChild(path);
        if (files.exists(path) || files.isSymbolicLink(path)) {
            throw failure("execution cgroup collision");
        }

        boolean created = false;
        try {
            files.createDirectory(path);
            created = true;
            validateEmptyOwnedParent(path);
            enableChildControllers(path);
            return new ExecutionCgroupLease(this, path);
        } catch (IOException | RuntimeException exception) {
            if (created) {
                rollbackNewParent(path, exception);
            }
            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }

    ExecutionCgroupSnapshot snapshot(Path path) throws IOException {
        validateOwnedParent(path);
        long memoryPeak = readCounter(control(path, "memory.peak"));
        Map<String, Long> memoryEvents = readEvents(control(path, "memory.events"));
        Map<String, Long> pidsEvents = readEvents(control(path, "pids.events"));
        return new ExecutionCgroupSnapshot(
                memoryPeak,
                requiredEvent(memoryEvents, "max", "memory.events"),
                requiredEvent(memoryEvents, "oom", "memory.events"),
                requiredEvent(memoryEvents, "oom_kill", "memory.events"),
                requiredEvent(pidsEvents, "max", "pids.events"));
    }

    void release(Path path) throws IOException {
        validateOwnedParent(path);
        requireEmpty(control(path, "cgroup.procs"), "execution cgroup still contains processes");
        List<Path> descendants = files.childCgroups(path);
        if (!descendants.isEmpty()) {
            throw failure("execution cgroup still contains descendants");
        }
        files.deleteDirectory(path);
        if (files.exists(path) || files.isSymbolicLink(path)) {
            throw failure("execution cgroup deletion could not be verified");
        }
    }

    private void validateDelegatedRoot() throws IOException {
        if (!files.isDirectory(delegatedRoot) || files.isSymbolicLink(delegatedRoot)
                || !files.isWritable(delegatedRoot)) {
            throw failure("configured delegated root is unavailable");
        }
    }

    private void validateEmptyOwnedParent(Path path) throws IOException {
        validateOwnedParent(path);
        requireEmpty(control(path, "cgroup.procs"), "new execution cgroup contains processes");
        if (!files.childCgroups(path).isEmpty()) {
            throw failure("new execution cgroup contains descendants");
        }
    }

    private void validateOwnedParent(Path path) throws IOException {
        requireDirectChild(path);
        if (!TRUSTED_NAME.matcher(path.getFileName().toString()).matches()) {
            throw failure("execution cgroup name is not trusted");
        }
        if (!files.isDirectory(path) || files.isSymbolicLink(path)) {
            throw failure("execution cgroup is unavailable");
        }
    }

    private void enableChildControllers(Path path) throws IOException {
        Path controllers = control(path, "cgroup.controllers");
        Path subtreeControl = control(path, "cgroup.subtree_control");
        Set<String> available = words(files.read(controllers));
        if (!available.containsAll(REQUIRED_CONTROLLERS)) {
            throw failure("execution cgroup controllers are unavailable");
        }
        if (!files.isWritable(subtreeControl)) {
            throw failure("execution cgroup subtree_control is not writable");
        }
        Set<String> enabled = words(files.read(subtreeControl));
        List<String> missing = REQUIRED_CONTROLLERS.stream()
                .filter(controller -> !enabled.contains(controller))
                .toList();
        if (!missing.isEmpty()) {
            files.write(subtreeControl, missing.stream()
                    .map(controller -> "+" + controller)
                    .collect(java.util.stream.Collectors.joining(" ")));
        }
        if (!words(files.read(subtreeControl)).containsAll(REQUIRED_CONTROLLERS)) {
            throw failure("execution cgroup controller enablement could not be verified");
        }
    }

    private Path control(Path parent, String name) throws IOException {
        Path path = parent.resolve(name).normalize();
        if (!path.getParent().equals(parent) || !path.startsWith(delegatedRoot)) {
            throw failure("cgroup control path escaped execution parent");
        }
        if (!files.isRegularFile(path) || files.isSymbolicLink(path)) {
            throw failure("cgroup control file is unavailable: " + name);
        }
        return path;
    }

    private void requireDirectChild(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.equals(path) || !delegatedRoot.equals(path.getParent())) {
            throw failure("execution cgroup escaped configured delegated root");
        }
    }

    private void requireEmpty(Path path, String message) throws IOException {
        if (!files.read(path).isBlank()) {
            throw failure(message);
        }
    }

    private long readCounter(Path path) throws IOException {
        try {
            long value = Long.parseLong(files.read(path).trim());
            if (value < 0) {
                throw failure("cgroup counter is negative: " + path.getFileName());
            }
            return value;
        } catch (NumberFormatException exception) {
            throw failure("cgroup counter is invalid: " + path.getFileName(), exception);
        }
    }

    private Map<String, Long> readEvents(Path path) throws IOException {
        Map<String, Long> events = new LinkedHashMap<>();
        for (String line : files.read(path).lines().toList()) {
            String[] fields = line.trim().split("\\s+");
            if (fields.length != 2 || fields[0].isBlank()) {
                throw failure("cgroup event file is malformed: " + path.getFileName());
            }
            long value;
            try {
                value = Long.parseLong(fields[1]);
            } catch (NumberFormatException exception) {
                throw failure("cgroup event counter is invalid: " + fields[0], exception);
            }
            if (value < 0 || events.putIfAbsent(fields[0], value) != null) {
                throw failure("cgroup event counter is invalid: " + fields[0]);
            }
        }
        return Map.copyOf(events);
    }

    private long requiredEvent(Map<String, Long> events, String name, String source) throws IOException {
        Long value = events.get(name);
        if (value == null) {
            throw failure("required cgroup event is missing: " + source + ":" + name);
        }
        return value;
    }

    private Set<String> words(String value) {
        return value.isBlank()
                ? Set.of()
                : new LinkedHashSet<>(List.of(value.trim().split("\\s+")));
    }

    private void rollbackNewParent(Path path, Exception original) {
        try {
            if (files.isDirectory(path) && !files.isSymbolicLink(path)
                    && files.read(control(path, "cgroup.procs")).isBlank()
                    && files.childCgroups(path).isEmpty()) {
                files.deleteDirectory(path);
            }
        } catch (IOException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private IOException failure(String message) {
        return new IOException("Linux sandbox execution cgroup failed: " + message);
    }

    private IOException failure(String message, Throwable cause) {
        return new IOException("Linux sandbox execution cgroup failed: " + message, cause);
    }

    interface CgroupFileAccess {
        boolean exists(Path path);
        boolean isDirectory(Path path);
        boolean isRegularFile(Path path);
        boolean isSymbolicLink(Path path);
        boolean isWritable(Path path);
        void createDirectory(Path path) throws IOException;
        String read(Path path) throws IOException;
        void write(Path path, String value) throws IOException;
        List<Path> childCgroups(Path path) throws IOException;
        void deleteDirectory(Path path) throws IOException;
    }

    private static final class NioCgroupFileAccess implements CgroupFileAccess {
        @Override
        public boolean exists(Path path) {
            return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
        }

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
        public void createDirectory(Path path) throws IOException {
            Files.createDirectory(path);
        }

        @Override
        public String read(Path path) throws IOException {
            return Files.readString(path, StandardCharsets.UTF_8);
        }

        @Override
        public void write(Path path, String value) throws IOException {
            Files.writeString(path, value, StandardCharsets.UTF_8, StandardOpenOption.WRITE);
        }

        @Override
        public List<Path> childCgroups(Path path) throws IOException {
            try (var entries = Files.list(path)) {
                return entries.filter(entry -> Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)
                                || Files.isSymbolicLink(entry))
                        .toList();
            }
        }

        @Override
        public void deleteDirectory(Path path) throws IOException {
            Files.delete(path);
        }
    }
}

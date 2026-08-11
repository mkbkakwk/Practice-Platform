package com.oj.runner.execution.linux;

import com.oj.runner.config.LinuxSandboxProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionCgroupManagerTest {

    private static final String ID_ONE = "11111111111111111111111111111111";
    private static final String ID_TWO = "22222222222222222222222222222222";

    @TempDir
    Path temporaryDirectory;

    private Path root;
    private FakeCgroupFiles files;

    @BeforeEach
    void setUp() {
        root = temporaryDirectory.resolve("delegated").toAbsolutePath().normalize();
        files = new FakeCgroupFiles(root);
    }

    @Test
    void allocatesOnlyFreshDirectParentsAndEnablesChildControllers() throws Exception {
        ExecutionCgroupLease lease = manager(ID_ONE).allocate();

        assertThat(lease.path()).isEqualTo(root.resolve("RUNNER." + ID_ONE));
        assertThat(lease.path().getParent()).isEqualTo(root);
        assertThat(files.read(lease.path().resolve("cgroup.subtree_control")))
                .contains("cpu", "memory", "pids");

        lease.close();
        assertThat(files.exists(lease.path())).isFalse();
    }

    @Test
    void rejectsSymlinkAndExistingDirectoryCollisions() {
        Path target = root.resolve("RUNNER." + ID_ONE);
        files.symlinks.add(target);
        assertThatThrownBy(() -> manager(ID_ONE).allocate())
                .isInstanceOf(IOException.class)
                .hasMessageContaining("collision");

        files.symlinks.clear();
        files.directories.add(target);
        assertThatThrownBy(() -> manager(ID_ONE).allocate())
                .isInstanceOf(IOException.class)
                .hasMessageContaining("collision");
    }

    @Test
    void cleanupFailsClosedForProcessesOrResidualDescendants() throws Exception {
        ExecutionCgroupLease occupied = manager(ID_ONE).allocate();
        files.values.put(occupied.path().resolve("cgroup.procs"), "42\n");
        assertThatThrownBy(occupied::close)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("contains processes");
        assertThat(files.exists(occupied.path())).isTrue();

        files.values.put(occupied.path().resolve("cgroup.procs"), "");
        files.deleteDirectory(occupied.path());
        ExecutionCgroupLease residual = manager(ID_TWO).allocate();
        Path child = residual.path().resolve("NSJAIL.1234");
        files.directories.add(child);
        assertThatThrownBy(residual::close)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("contains descendants");
        assertThat(files.exists(residual.path())).isTrue();
    }

    @Test
    void postMortemCountersRemainPerInvocationAfterNsjailChildDisappears() throws Exception {
        Queue<String> ids = new ArrayDeque<>(List.of(ID_ONE, ID_TWO));
        ExecutionCgroupManager manager = manager(ids::remove);
        ExecutionCgroupLease first = manager.allocate();
        ExecutionCgroupLease second = manager.allocate();

        Path child = first.path().resolve("NSJAIL.9876");
        files.directories.add(child);
        files.directories.remove(child);
        files.setCounters(first.path(), 66_000_000, 4, 2, 1, 7);
        files.setCounters(second.path(), 1_024, 0, 0, 0, 0);

        ExecutionCgroupSnapshot firstSnapshot = first.snapshot();
        ExecutionCgroupSnapshot secondSnapshot = second.snapshot();
        assertThat(firstSnapshot.oomKillEvents()).isEqualTo(1);
        assertThat(firstSnapshot.memoryMaxEvents()).isEqualTo(4);
        assertThat(firstSnapshot.pidsMaxEvents()).isEqualTo(7);
        assertThat(secondSnapshot.oomKillEvents()).isZero();
        assertThat(secondSnapshot.memoryPeakBytes()).isEqualTo(1_024);

        first.close();
        second.close();
        assertThat(files.childCgroups(root)).isEmpty();
    }

    @Test
    void deletionFailureIsFailClosed() throws Exception {
        ExecutionCgroupLease lease = manager(ID_ONE).allocate();
        files.failDelete = true;

        assertThatThrownBy(lease::close)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("simulated delete failure");
        assertThat(files.exists(lease.path())).isTrue();
    }

    private ExecutionCgroupManager manager(String id) {
        return manager(() -> id);
    }

    private ExecutionCgroupManager manager(java.util.function.Supplier<String> ids) {
        LinuxSandboxProperties properties = new LinuxSandboxProperties();
        properties.setCgroupV2Mount(root.toString());
        return new ExecutionCgroupManager(properties, files, ids);
    }

    private static final class FakeCgroupFiles implements ExecutionCgroupManager.CgroupFileAccess {
        private final Path root;
        private final Set<Path> directories = new HashSet<>();
        private final Set<Path> symlinks = new HashSet<>();
        private final Set<Path> writable = new HashSet<>();
        private final Map<Path, String> values = new HashMap<>();
        private boolean failDelete;

        private FakeCgroupFiles(Path root) {
            this.root = root;
            directories.add(root);
            writable.add(root);
        }

        private void setCounters(
                Path parent,
                long peak,
                long max,
                long oom,
                long oomKill,
                long pidsMax) {
            values.put(parent.resolve("memory.peak"), Long.toString(peak));
            values.put(parent.resolve("memory.events"),
                    "low 0\nhigh 0\nmax " + max + "\noom " + oom
                            + "\noom_kill " + oomKill + "\noom_group_kill 0\n");
            values.put(parent.resolve("pids.events"), "max " + pidsMax + "\n");
        }

        @Override
        public boolean exists(Path path) {
            return directories.contains(path) || symlinks.contains(path) || values.containsKey(path);
        }

        @Override
        public boolean isDirectory(Path path) {
            return directories.contains(path);
        }

        @Override
        public boolean isRegularFile(Path path) {
            return values.containsKey(path);
        }

        @Override
        public boolean isSymbolicLink(Path path) {
            return symlinks.contains(path);
        }

        @Override
        public boolean isWritable(Path path) {
            return writable.contains(path);
        }

        @Override
        public void createDirectory(Path path) {
            directories.add(path);
            writable.add(path);
            values.put(path.resolve("cgroup.procs"), "");
            values.put(path.resolve("cgroup.controllers"), "cpu memory pids");
            values.put(path.resolve("cgroup.subtree_control"), "");
            writable.add(path.resolve("cgroup.subtree_control"));
            setCounters(path, 0, 0, 0, 0, 0);
        }

        @Override
        public String read(Path path) throws IOException {
            String value = values.get(path);
            if (value == null) {
                throw new IOException("missing fake cgroup file: " + path);
            }
            return value;
        }

        @Override
        public void write(Path path, String value) throws IOException {
            if (!writable.contains(path) || !path.getFileName().toString().equals("cgroup.subtree_control")) {
                throw new IOException("write outside subtree_control");
            }
            Set<String> enabled = new HashSet<>();
            String current = values.getOrDefault(path, "");
            if (!current.isBlank()) {
                enabled.addAll(List.of(current.trim().split("\\s+")));
            }
            for (String token : value.split("\\s+")) {
                if (!token.startsWith("+")) {
                    throw new IOException("invalid controller request");
                }
                enabled.add(token.substring(1));
            }
            values.put(path, String.join(" ", enabled));
        }

        @Override
        public List<Path> childCgroups(Path path) {
            List<Path> children = new ArrayList<>();
            directories.stream().filter(entry -> path.equals(entry.getParent())).forEach(children::add);
            symlinks.stream().filter(entry -> path.equals(entry.getParent())).forEach(children::add);
            return List.copyOf(children);
        }

        @Override
        public void deleteDirectory(Path path) throws IOException {
            if (failDelete) {
                throw new IOException("simulated delete failure");
            }
            directories.remove(path);
            writable.remove(path);
            writable.removeIf(entry -> path.equals(entry.getParent()));
            values.keySet().removeIf(entry -> path.equals(entry.getParent()));
        }
    }
}

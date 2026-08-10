package com.oj.runner.execution.linux;

import com.oj.runner.config.LinuxSandboxProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DelegatedCgroupControllerInitializerTest {

    private static final Path ROOT = Path.of(System.getProperty("java.io.tmpdir"))
            .toAbsolutePath().normalize().resolve("delegated-oj-runner");

    @Test
    void enablesAllRequiredControllersWhenNoneAreEnabled() {
        FakeCgroupFiles files = new FakeCgroupFiles();

        initializer(files).initialize();

        assertThat(files.enabled).containsExactlyInAnyOrder("cpu", "memory", "pids");
        assertThat(files.writes).containsExactly(new Write(
                ROOT.resolve("cgroup.subtree_control"), "+cpu +memory +pids"));
        assertAccessConfinedToConfiguredRoot(files);
    }

    @Test
    void enablesOnlyControllersThatAreStillMissing() {
        FakeCgroupFiles files = new FakeCgroupFiles();
        files.enabled.add("cpu");

        initializer(files).initialize();

        assertThat(files.writes).containsExactly(new Write(
                ROOT.resolve("cgroup.subtree_control"), "+memory +pids"));
        assertThat(files.enabled).containsExactlyInAnyOrder("cpu", "memory", "pids");
    }

    @Test
    void isIdempotentWhenAllControllersAreAlreadyEnabled() {
        FakeCgroupFiles files = new FakeCgroupFiles();
        files.enabled.addAll(List.of("cpu", "memory", "pids"));

        initializer(files).initialize();

        assertThat(files.writes).isEmpty();
    }

    @Test
    void failsClosedWhenARequiredControllerIsUnavailable() {
        FakeCgroupFiles files = new FakeCgroupFiles();
        files.available.remove("memory");

        assertThatThrownBy(() -> initializer(files).initialize())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("required cgroup controllers are unavailable")
                .hasMessageContaining("memory");
        assertThat(files.writes).isEmpty();
    }

    @Test
    void failsClosedWhenConfiguredDelegatedRootIsMissing() {
        FakeCgroupFiles files = new FakeCgroupFiles();
        files.rootExists = false;

        assertThatThrownBy(() -> initializer(files).initialize())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delegated cgroup root is unavailable");
        assertThat(files.writes).isEmpty();
    }

    @Test
    void failsClosedWhenSubtreeControlIsNotWritable() {
        FakeCgroupFiles files = new FakeCgroupFiles();
        files.writable = false;

        assertThatThrownBy(() -> initializer(files).initialize())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not writable");
        assertThat(files.writes).isEmpty();
    }

    @Test
    void failsClosedWhenControllerWriteFails() {
        FakeCgroupFiles files = new FakeCgroupFiles();
        files.failWrite = true;

        assertThatThrownBy(() -> initializer(files).initialize())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot enable delegated cgroup controllers");
    }

    @Test
    void failsClosedWhenControllerWriteDoesNotTakeEffect() {
        FakeCgroupFiles files = new FakeCgroupFiles();
        files.applyWrites = false;

        assertThatThrownBy(() -> initializer(files).initialize())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("enablement could not be verified");
    }

    @Test
    void doesNotCreateInitializerOutsideLinuxSandboxMode() {
        new ApplicationContextRunner()
                .withUserConfiguration(InitializerConfiguration.class)
                .withPropertyValues("runner.sandbox.mode=unavailable")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(DelegatedCgroupControllerInitializer.class));
    }

    @Test
    void preflightExplicitlyDependsOnControllerInitialization() {
        DependsOn dependsOn = LinuxSandboxPreflight.class.getAnnotation(DependsOn.class);

        assertThat(dependsOn).isNotNull();
        assertThat(dependsOn.value()).containsExactly("delegatedCgroupControllerInitializer");
    }

    private DelegatedCgroupControllerInitializer initializer(FakeCgroupFiles files) {
        LinuxSandboxProperties properties = new LinuxSandboxProperties();
        properties.setCgroupV2Mount(ROOT.toString());
        return new DelegatedCgroupControllerInitializer(properties, files);
    }

    private void assertAccessConfinedToConfiguredRoot(FakeCgroupFiles files) {
        assertThat(files.accesses).allSatisfy(path -> {
            assertThat(path.normalize().startsWith(ROOT)).isTrue();
            assertThat(path).isIn(
                    ROOT,
                    ROOT.resolve("cgroup.controllers"),
                    ROOT.resolve("cgroup.subtree_control"));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LinuxSandboxProperties.class)
    @Import(DelegatedCgroupControllerInitializer.class)
    static class InitializerConfiguration {
    }

    private record Write(Path path, String request) {
    }

    private static final class FakeCgroupFiles
            implements DelegatedCgroupControllerInitializer.CgroupFileAccess {
        private final Set<String> available = new LinkedHashSet<>(List.of("cpu", "memory", "pids", "io"));
        private final Set<String> enabled = new LinkedHashSet<>();
        private final List<Path> accesses = new ArrayList<>();
        private final List<Write> writes = new ArrayList<>();
        private boolean rootExists = true;
        private boolean writable = true;
        private boolean failWrite;
        private boolean applyWrites = true;

        @Override
        public boolean isDirectory(Path path) {
            accesses.add(path);
            return rootExists && path.equals(ROOT);
        }

        @Override
        public boolean isRegularFile(Path path) {
            accesses.add(path);
            return path.equals(ROOT.resolve("cgroup.controllers"))
                    || path.equals(ROOT.resolve("cgroup.subtree_control"));
        }

        @Override
        public boolean isSymbolicLink(Path path) {
            accesses.add(path);
            return false;
        }

        @Override
        public boolean isWritable(Path path) {
            accesses.add(path);
            return writable && path.equals(ROOT.resolve("cgroup.subtree_control"));
        }

        @Override
        public String read(Path path) throws IOException {
            accesses.add(path);
            if (path.equals(ROOT.resolve("cgroup.controllers"))) {
                return String.join(" ", available);
            }
            if (path.equals(ROOT.resolve("cgroup.subtree_control"))) {
                return String.join(" ", enabled);
            }
            throw new IOException("read outside configured root");
        }

        @Override
        public void write(Path path, String request) throws IOException {
            accesses.add(path);
            writes.add(new Write(path, request));
            if (failWrite) {
                throw new IOException("simulated delegated cgroup write failure");
            }
            if (!path.equals(ROOT.resolve("cgroup.subtree_control"))) {
                throw new IOException("write outside configured root");
            }
            if (!applyWrites) {
                return;
            }
            for (String token : request.split("\\s+")) {
                if (!token.startsWith("+") || !available.contains(token.substring(1))) {
                    throw new IOException("unexpected controller request");
                }
                enabled.add(token.substring(1));
            }
        }
    }
}

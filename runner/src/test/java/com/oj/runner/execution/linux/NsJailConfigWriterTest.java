package com.oj.runner.execution.linux;

import com.oj.runner.api.RunnerLanguage;
import com.oj.runner.config.LinuxSandboxProperties;
import com.oj.runner.language.LanguageProfileRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NsJailConfigWriterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void configEnforcesNamespacesCgroupsReadOnlyRootAndMinimalEnvironment() throws Exception {
        LinuxSandboxProperties properties = properties();
        SandboxWorkspace workspace = workspace();
        var profile = new LanguageProfileRegistry().require(RunnerLanguage.JAVA);
        Path executionCgroup = executionCgroup(properties, "11111111111111111111111111111111");
        Path config = new NsJailConfigWriter(properties).write(
                workspace, profile, "compile", 2000, 256, executionCgroup);

        String value = Files.readString(config);
        assertThat(value).contains(
                "mode: ONCE",
                "clone_newnet: true", "clone_newuser: true", "clone_newns: true",
                "clone_newpid: true", "clone_newipc: true", "clone_newuts: true",
                "clone_newcgroup: true", "clone_newtime: true", "iface_no_lo: true",
                "keep_env: false", "keep_caps: false", "disable_no_new_privs: false",
                "use_cgroupv2: true", "cgroup_mem_max: 268435456",
                "cgroup_mem_swap_max: 0", "cgroup_pids_max: 32",
                "cgroup_cpu_ms_per_sec: 1000", "seccomp_policy_file:",
                "dst: \"/\" is_bind: true rw: false", "dst: \"/workspace\" is_bind: true rw: true",
                "dst: \"/dev\" fstype: \"tmpfs\" rw: true", "src: \"/dev/null\"",
                "dst: \"/tmp\" fstype: \"tmpfs\" rw: true", "noexec: true",
                "envar: \"JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64\"",
                "cgroupv2_mount: \"" + executionCgroup + "\"");
        assertThat(value).doesNotContain("mode: EXECVE");
        assertThat(value).doesNotContain("dst: \"/\" is_bind: true rw: true");
        assertThat(value).doesNotContain("RUNNER_TOKEN", "DATABASE_URL", "JWT", "sourceCode");

        assertBackupDeadlinesAreStrictlyLaterThanOuterDeadline(500, "timeout-500");
        assertBackupDeadlinesAreStrictlyLaterThanOuterDeadline(1000, "timeout-1000");
        assertBackupDeadlinesAreStrictlyLaterThanOuterDeadline(1001, "timeout-1001");
        assertBackupDeadlinesAreStrictlyLaterThanOuterDeadline(60_000, "timeout-large");
    }

    @Test
    void phaseIdCannotEscapeMetadataDirectory() throws Exception {
        NsJailConfigWriter writer = new NsJailConfigWriter(properties());
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> writer.write(
                workspace(), new LanguageProfileRegistry().require(RunnerLanguage.C),
                "../escape", 1000, 64,
                executionCgroup(properties(), "22222222222222222222222222222222"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void configUsesExplicitReadOnlySubsetPidProcMount() throws Exception {
        Path config = new NsJailConfigWriter(properties()).write(
                workspace(), new LanguageProfileRegistry().require(RunnerLanguage.PYTHON),
                "run-1", 1000, 64,
                executionCgroup(properties(), "33333333333333333333333333333333"));

        String value = Files.readString(config);
        assertThat(value).doesNotContain("mount_proc: true");
        assertThat(value).contains(
                "mount { dst: \"/proc\" fstype: \"proc\" options: \"subset=pid\" "
                        + "rw: false mandatory: true nosuid: true nodev: true noexec: true }");
    }

    @Test
    void configBindsOnlyFourWhitelistedCharacterDevicesWithCompatibleFlags() throws Exception {
        Path config = new NsJailConfigWriter(properties()).write(
                workspace(), new LanguageProfileRegistry().require(RunnerLanguage.PYTHON),
                "run-1", 1000, 64,
                executionCgroup(properties(), "44444444444444444444444444444444"));

        String value = Files.readString(config);
        assertThat(value).contains(
                "mount { dst: \"/dev\" fstype: \"tmpfs\" rw: true mandatory: true "
                        + "nosuid: true nodev: false noexec: true options: \"size=65536,mode=0755\" }");
        List<String> deviceMounts = value.lines()
                .filter(line -> line.startsWith("mount { src: \"/dev/"))
                .toList();
        assertThat(deviceMounts).containsExactly(
                deviceMount("/dev/null"),
                deviceMount("/dev/zero"),
                deviceMount("/dev/random"),
                deviceMount("/dev/urandom"));
    }

    private String deviceMount(String device) {
        return "mount { src: \"" + device + "\" dst: \"" + device
                + "\" is_bind: true is_dir: false rw: false mandatory: true "
                + "nosuid: false nodev: false noexec: false }";
    }

    private void assertBackupDeadlinesAreStrictlyLaterThanOuterDeadline(
            long wallTimeMs,
            String phaseId) throws Exception {
        Path config = new NsJailConfigWriter(properties()).write(
                workspace(), new LanguageProfileRegistry().require(RunnerLanguage.C),
                phaseId, wallTimeMs, 64,
                executionCgroup(properties(), "55555555555555555555555555555555"));
        String value = Files.readString(config);

        long timeLimitSeconds = numericValue(value, "time_limit");
        long cpuLimitSeconds = numericValue(value, "rlimit_cpu");
        assertThat(Math.multiplyExact(timeLimitSeconds, 1000L)).isGreaterThan(wallTimeMs);
        assertThat(Math.multiplyExact(cpuLimitSeconds, 1000L)).isGreaterThan(wallTimeMs);
    }

    private long numericValue(String config, String key) {
        return config.lines()
                .filter(line -> line.startsWith(key + ": "))
                .map(line -> line.substring((key + ": ").length()))
                .mapToLong(Long::parseLong)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing nsjail config key: " + key));
    }

    private LinuxSandboxProperties properties() {
        LinuxSandboxProperties properties = new LinuxSandboxProperties();
        properties.setRootfs("/srv/oj-sandbox-runner/rootfs");
        properties.setSeccompPolicy("/etc/oj-sandbox-runner/nsjail-seccomp.policy");
        properties.setCgroupV2Mount("/sys/fs/cgroup/system.slice/oj-sandbox-runner.service");
        return properties;
    }

    private Path executionCgroup(LinuxSandboxProperties properties, String id) {
        return Path.of(properties.getCgroupV2Mount()).resolve("RUNNER." + id);
    }

    private SandboxWorkspace workspace() throws Exception {
        Path root = Files.createTempDirectory(temporaryDirectory, "job-");
        Path files = Files.createDirectory(root.resolve("workspace"));
        Path metadata = Files.createDirectory(root.resolve("metadata"));
        return new SandboxWorkspace(root, files, metadata);
    }
}

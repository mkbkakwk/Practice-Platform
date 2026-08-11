package com.oj.runner.execution.linux;

import com.oj.runner.config.LinuxSandboxProperties;
import com.oj.runner.language.LanguageProfile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.TreeMap;

@Component
public class NsJailConfigWriter {

    private static final long BYTES_PER_MIB = 1024L * 1024L;

    private final LinuxSandboxProperties properties;

    public NsJailConfigWriter(LinuxSandboxProperties properties) {
        this.properties = properties;
    }

    public Path write(
            SandboxWorkspace workspace,
            LanguageProfile profile,
            String phaseId,
            long wallTimeMs,
            long memoryMb) throws IOException {
        validatePhaseId(phaseId);
        Path config = workspace.metadata().resolve("nsjail-" + phaseId + ".cfg");
        Path log = workspace.metadata().resolve("nsjail-" + phaseId + ".log");
        long memoryBytes = Math.multiplyExact(memoryMb, BYTES_PER_MIB);
        long wallSeconds = Math.max(1, Math.ceilDiv(wallTimeMs, 1000));
        long maxFileMib = Math.max(1, Math.ceilDiv(properties.getMaxFileBytes(), BYTES_PER_MIB));

        StringBuilder value = new StringBuilder();
        line(value, "name", "practice-platform-student-sandbox");
        value.append("mode: ONCE\n")
                .append("hostname: \"student-sandbox\"\n")
                .append("cwd: \"/workspace\"\n")
                .append("time_limit: ").append(wallSeconds).append('\n')
                .append("max_cpus: 1\n")
                .append("nice_level: 19\n")
                .append("keep_env: false\n")
                .append("keep_caps: false\n")
                .append("disable_no_new_privs: false\n")
                .append("skip_setsid: false\n")
                .append("forward_signals: false\n")
                .append("rlimit_as_type: INF\n")
                .append("rlimit_core: 0\n")
                .append("rlimit_core_type: VALUE\n")
                .append("rlimit_cpu: ").append(wallSeconds).append('\n')
                .append("rlimit_cpu_type: VALUE\n")
                .append("rlimit_fsize: ").append(maxFileMib).append('\n')
                .append("rlimit_fsize_type: VALUE\n")
                .append("rlimit_nofile: ").append(properties.getMaxOpenFiles()).append('\n')
                .append("rlimit_nofile_type: VALUE\n")
                .append("rlimit_nproc: ").append(properties.getPidsMax()).append('\n')
                .append("rlimit_nproc_type: VALUE\n")
                .append("rlimit_stack: 64\n")
                .append("rlimit_stack_type: VALUE\n")
                .append("clone_newnet: true\n")
                .append("clone_newuser: true\n")
                .append("clone_newns: true\n")
                .append("clone_newpid: true\n")
                .append("clone_newipc: true\n")
                .append("clone_newuts: true\n")
                .append("clone_newcgroup: true\n")
                .append("clone_newtime: true\n")
                .append("iface_no_lo: true\n")
                .append("oom_score_adj: 500\n")
                .append("use_cgroupv2: true\n")
                .append("cgroup_mem_max: ").append(memoryBytes).append('\n')
                .append("cgroup_mem_swap_max: 0\n")
                .append("cgroup_pids_max: ").append(properties.getPidsMax()).append('\n')
                .append("cgroup_cpu_ms_per_sec: ").append(properties.getCpuMsPerSecond()).append('\n');
        line(value, "cgroupv2_mount", properties.getCgroupV2Mount());
        line(value, "seccomp_policy_file", properties.getSeccompPolicy());
        line(value, "log_file", log.toString());
        value.append("log_level: WARNING\n");

        value.append("uidmap { inside_id: \"").append(properties.getStudentUid())
                .append("\" outside_id: \"\" count: 1 use_newidmap: false }\n");
        value.append("gidmap { inside_id: \"").append(properties.getStudentGid())
                .append("\" outside_id: \"\" count: 1 use_newidmap: false }\n");

        mount(value, properties.getRootfs(), "/", false, true, true, false);
        mount(value, workspace.files().toString(), "/workspace", true, true, true, false);
        value.append("mount { dst: \"/proc\" fstype: \"proc\" options: \"subset=pid\" ")
                .append("rw: false mandatory: true nosuid: true nodev: true noexec: true }\n");
        value.append("mount { dst: \"/dev\" fstype: \"tmpfs\" rw: true mandatory: true ")
                .append("nosuid: true nodev: false noexec: true options: \"size=65536,mode=0755\" }\n");
        for (String device : java.util.List.of("/dev/null", "/dev/zero", "/dev/random", "/dev/urandom")) {
            value.append("mount { src: \"").append(device).append("\" dst: \"").append(device)
                    .append("\" is_bind: true is_dir: false rw: true mandatory: true ")
                    .append("nosuid: false nodev: false noexec: false }\n");
        }
        value.append("mount { dst: \"/tmp\" fstype: \"tmpfs\" rw: true mandatory: true ")
                .append("nosuid: true nodev: true noexec: true options: \"")
                .append("size=").append(properties.getTmpfsBytes()).append(",mode=1777\" }\n");

        for (Map.Entry<String, String> environment : new TreeMap<>(profile.environment()).entrySet()) {
            line(value, "envar", environment.getKey() + "=" + environment.getValue());
        }

        Files.writeString(config, value.toString(), StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(config, PosixFilePermissions.fromString("rw-------"));
        return config;
    }

    public Path logPath(SandboxWorkspace workspace, String phaseId) {
        validatePhaseId(phaseId);
        return workspace.metadata().resolve("nsjail-" + phaseId + ".log");
    }

    private void mount(
            StringBuilder value,
            String source,
            String destination,
            boolean writable,
            boolean nosuid,
            boolean nodev,
            boolean noexec) {
        value.append("mount { src: \"").append(escape(source)).append("\" dst: \"")
                .append(escape(destination)).append("\" is_bind: true rw: ").append(writable)
                .append(" mandatory: true nosuid: ").append(nosuid)
                .append(" nodev: ").append(nodev).append(" noexec: ").append(noexec).append(" }\n");
    }

    private void line(StringBuilder value, String key, String content) {
        value.append(key).append(": \"").append(escape(content)).append("\"\n");
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private void validatePhaseId(String phaseId) {
        if (phaseId == null || !phaseId.matches("[a-z0-9-]{1,32}")) {
            throw new IllegalArgumentException("Sandbox phase id is invalid");
        }
    }
}

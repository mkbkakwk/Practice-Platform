package com.oj.runner.execution.linux;

import com.oj.runner.config.LinuxSandboxProperties;
import com.oj.runner.language.LanguageProfileRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@Profile("!runner-contract-test")
@ConditionalOnProperty(prefix = "runner.sandbox", name = "mode", havingValue = "linux")
public class LinuxSandboxPreflight {

    private static final Logger log = LoggerFactory.getLogger(LinuxSandboxPreflight.class);
    private static final Set<String> REQUIRED_CONTROLLERS = Set.of("cpu", "memory", "pids");
    private static final List<String> REQUIRED_NAMESPACES = List.of(
            "mnt", "pid", "net", "uts", "ipc", "user", "cgroup", "time");

    private final LinuxSandboxProperties properties;
    private final LanguageProfileRegistry profileRegistry;
    private final SandboxWorkspaceManager workspaceManager;
    private final NsJailConfigWriter configWriter;
    private final LanguageCommandResolver commandResolver;
    private final SandboxProcessLauncher launcher;
    private volatile SandboxAvailability availability = new SandboxAvailability(false, List.of("not checked"));

    public LinuxSandboxPreflight(
            LinuxSandboxProperties properties,
            LanguageProfileRegistry profileRegistry,
            SandboxWorkspaceManager workspaceManager,
            NsJailConfigWriter configWriter,
            LanguageCommandResolver commandResolver,
            SandboxProcessLauncher launcher) {
        this.properties = properties;
        this.profileRegistry = profileRegistry;
        this.workspaceManager = workspaceManager;
        this.configWriter = configWriter;
        this.commandResolver = commandResolver;
        this.launcher = launcher;
    }

    @PostConstruct
    void initialize() {
        availability = inspect();
        if (availability.supported()) {
            log.info("Linux sandbox preflight SUPPORTED");
        } else {
            log.warn("Linux sandbox preflight UNSUPPORTED checks={}", String.join(",", availability.reasons()));
        }
    }

    public SandboxAvailability availability() {
        return availability;
    }

    SandboxAvailability inspect() {
        List<String> failures = new ArrayList<>();
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) {
            failures.add("host-not-linux");
        }
        if (Files.exists(Path.of("/.dockerenv"))) {
            failures.add("container-host-unsupported");
        }
        read(Path.of("/proc/version")).map(value -> value.toLowerCase(Locale.ROOT)).ifPresent(value -> {
            if (value.contains("microsoft") || value.contains("wsl")) {
                failures.add("wsl-docker-desktop-unsupported");
            }
        });
        inspectIdentity(failures);
        inspectNamespaces(failures);
        inspectNsjail(failures);
        inspectRootfs(failures);
        inspectWorkspace(failures);
        inspectCgroup(failures);
        inspectSeccomp(failures);
        if (failures.isEmpty() && !executeSelfTest()) {
            failures.add("sandbox-self-test-failed");
        }
        return new SandboxAvailability(failures.isEmpty(), failures);
    }

    private boolean executeSelfTest() {
        SandboxWorkspace workspace = null;
        boolean success = false;
        try {
            var profile = profileRegistry.require(com.oj.runner.api.RunnerLanguage.PYTHON);
            workspace = workspaceManager.create("00000000-0000-4000-8000-000000000000");
            workspaceManager.writeSource(workspace, profile.sourceFilename(), "print('sandbox-self-test')\n");
            Path config = configWriter.write(workspace, profile, "self-test", 2000, 128);
            NsJailExecutionResult result = launcher.launch(new NsJailInvocation(
                    SandboxPhase.COMPILE,
                    config,
                    configWriter.logPath(workspace, "self-test"),
                    workspace.files(),
                    commandResolver.compile(profile, 128),
                    new byte[0],
                    2000,
                    128,
                    4096));
            success = result.termination() == SandboxTermination.COMPLETED && result.exitCode() == 0;
        } catch (IOException | RuntimeException exception) {
            success = false;
        } finally {
            if (workspace != null) {
                try {
                    workspaceManager.cleanup(workspace);
                } catch (IOException exception) {
                    success = false;
                }
            }
        }
        return success;
    }

    private void inspectIdentity(List<String> failures) {
        read(Path.of("/proc/self/status")).ifPresentOrElse(status -> {
            String uid = lineValue(status, "Uid:");
            String effectiveCaps = lineValue(status, "CapEff:");
            if (uid == null || uid.split("\\s+")[0].equals("0")) {
                failures.add("runner-must-be-non-root");
            }
            if (effectiveCaps == null || !effectiveCaps.matches("0+")) {
                failures.add("runner-capabilities-must-be-empty");
            }
        }, () -> failures.add("proc-status-unavailable"));
    }

    private void inspectNamespaces(List<String> failures) {
        for (String namespace : REQUIRED_NAMESPACES) {
            if (!Files.exists(Path.of("/proc/self/ns", namespace))) {
                failures.add("namespace-missing-" + namespace);
            }
        }
    }

    private void inspectNsjail(List<String> failures) {
        Path nsjail = trustedPath(properties.getNsjailPath(), "nsjail-path", failures);
        if (nsjail != null && (!Files.isRegularFile(nsjail) || !Files.isExecutable(nsjail))) {
            failures.add("nsjail-not-executable");
        }
        if (nsjail != null && !NsJailLauncher.probeVersion(nsjail)) {
            failures.add("nsjail-version-check-failed");
        }
    }

    private void inspectRootfs(List<String> failures) {
        Path rootfs = trustedPath(properties.getRootfs(), "rootfs-path", failures);
        if (rootfs == null || !Files.isDirectory(rootfs, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(rootfs)) {
            failures.add("rootfs-unavailable");
            return;
        }
        if (Files.isWritable(rootfs)) {
            failures.add("rootfs-must-be-read-only");
        }
        for (String directory : List.of("dev", "proc", "tmp", "workspace")) {
            if (!Files.isDirectory(rootfs.resolve(directory), LinkOption.NOFOLLOW_LINKS)) {
                failures.add("rootfs-directory-missing-" + directory);
            }
        }
        Set<String> required = new HashSet<>();
        profileRegistry.profiles().values().forEach(profile -> required.addAll(profile.requiredRuntimePaths()));
        for (String runtimePath : required) {
            Path relative = Path.of(runtimePath.substring(1));
            Path candidate = rootfs.resolve(relative).normalize();
            if (!candidate.startsWith(rootfs) || !Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                failures.add("runtime-path-missing-" + relative.toString().replace('/', '-'));
            }
        }
    }

    private void inspectWorkspace(List<String> failures) {
        Path workspace = trustedPath(properties.getWorkspaceRoot(), "workspace-path", failures);
        if (workspace == null || !Files.isDirectory(workspace, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(workspace) || !Files.isWritable(workspace)) {
            failures.add("workspace-unavailable");
            return;
        }
        if (!"tmpfs".equals(filesystemType(workspace))) {
            failures.add("workspace-must-be-tmpfs");
        }
    }

    private void inspectCgroup(List<String> failures) {
        Path root = trustedPath(properties.getCgroupV2Mount(), "cgroup-path", failures);
        if (root == null || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(root)) {
            failures.add("cgroup-v2-root-unavailable");
            return;
        }
        Set<String> controllers = words(read(root.resolve("cgroup.controllers")).orElse(""));
        if (!controllers.containsAll(REQUIRED_CONTROLLERS)) {
            failures.add("cgroup-controllers-missing");
        }
        Set<String> enabled = words(read(root.resolve("cgroup.subtree_control")).orElse(""));
        if (!enabled.containsAll(REQUIRED_CONTROLLERS)) {
            failures.add("cgroup-controllers-not-enabled");
        }
        if (!Files.isWritable(root) || !Files.isWritable(root.resolve("cgroup.subtree_control"))) {
            failures.add("cgroup-v2-not-delegated");
        }
        if (!read(root.resolve("cgroup.procs")).orElse("occupied").isBlank()) {
            failures.add("cgroup-v2-root-not-empty");
        }
    }

    private void inspectSeccomp(List<String> failures) {
        Path policy = trustedPath(properties.getSeccompPolicy(), "seccomp-path", failures);
        if (policy == null || !Files.isRegularFile(policy) || !Files.isReadable(policy)) {
            failures.add("seccomp-policy-unavailable");
        }
    }

    private Path trustedPath(String value, String label, List<String> failures) {
        try {
            Path path = Path.of(value);
            if (!path.isAbsolute() || value.contains("\n") || value.contains("\r")) {
                failures.add(label + "-invalid");
                return null;
            }
            return path.normalize();
        } catch (RuntimeException exception) {
            failures.add(label + "-invalid");
            return null;
        }
    }

    private String filesystemType(Path target) {
        String mountInfo = read(Path.of("/proc/self/mountinfo")).orElse("");
        Path best = null;
        String type = null;
        for (String line : mountInfo.lines().toList()) {
            String[] sections = line.split(" - ", 2);
            if (sections.length != 2) {
                continue;
            }
            String[] left = sections[0].split(" ");
            String[] right = sections[1].split(" ");
            if (left.length < 5 || right.length < 1) {
                continue;
            }
            Path mount = Path.of(unescapeMount(left[4])).normalize();
            if (target.startsWith(mount) && (best == null || mount.getNameCount() > best.getNameCount())) {
                best = mount;
                type = right[0];
            }
        }
        return type;
    }

    private String unescapeMount(String value) {
        return value.replace("\\040", " ").replace("\\011", "\t").replace("\\134", "\\");
    }

    private Set<String> words(String value) {
        return value.isBlank() ? Set.of() : Set.of(value.trim().split("\\s+"));
    }

    private String lineValue(String content, String key) {
        return content.lines().filter(line -> line.startsWith(key))
                .map(line -> line.substring(key.length()).trim()).findFirst().orElse(null);
    }

    private java.util.Optional<String> read(Path path) {
        try {
            return java.util.Optional.of(Files.readString(path));
        } catch (IOException exception) {
            return java.util.Optional.empty();
        }
    }
}

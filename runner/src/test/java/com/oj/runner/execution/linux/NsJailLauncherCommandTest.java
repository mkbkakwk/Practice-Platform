package com.oj.runner.execution.linux;

import com.oj.runner.config.LinuxSandboxProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NsJailLauncherCommandTest {

    @Test
    void usesNewMountApiBeforeConfigAndKeepsExecutionArgvAfterSeparator() {
        LinuxSandboxProperties properties = new LinuxSandboxProperties();
        properties.setNsjailPath("/usr/bin/nsjail");
        properties.setCgroupV2Mount("/sys/fs/cgroup/runner");
        NsJailLauncher launcher = new NsJailLauncher(properties);
        NsJailInvocation invocation = new NsJailInvocation(
                SandboxPhase.RUN,
                Path.of("/run/runner/nsjail.cfg"),
                Path.of("/run/runner/nsjail.log"),
                Path.of("/run/runner/workspace"),
                List.of("/usr/bin/python3", "--experimental_mnt", "old"),
                new byte[0],
                1000,
                128,
                4096);

        List<String> command = launcher.buildCommand(invocation);
        assertThat(command).containsExactly(
                "/usr/bin/nsjail",
                "--experimental_mnt", "new",
                "--rw",
                "--config", "/run/runner/nsjail.cfg",
                "--",
                "/usr/bin/python3", "--experimental_mnt", "old");
    }
}

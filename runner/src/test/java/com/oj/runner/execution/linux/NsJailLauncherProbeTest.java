package com.oj.runner.execution.linux;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;

class NsJailLauncherProbeTest {

    @TempDir
    Path tempDir;

    @Test
    void acceptsExecutableWhoseHelpCommandSucceeds() throws IOException {
        Path nsjail = executable("nsjail-ok", "#!/bin/sh\n[ \"$1\" = \"--help\" ]\n");

        assertThat(NsJailLauncher.probeHelp(nsjail)).isTrue();
    }

    @Test
    void rejectsExecutableWhoseHelpCommandFails() throws IOException {
        Path nsjail = executable("nsjail-fail", "#!/bin/sh\nexit 9\n");

        assertThat(NsJailLauncher.probeHelp(nsjail)).isFalse();
    }

    @Test
    void rejectsMissingExecutable() {
        assertThat(NsJailLauncher.probeHelp(tempDir.resolve("missing-nsjail"))).isFalse();
    }

    private Path executable(String name, String content) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
        return path;
    }
}

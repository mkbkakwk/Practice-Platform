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
    void acceptsExecutableWhoseHelpSupportsNewMountApi() throws IOException {
        Path nsjail = executable("nsjail-ok", """
                #!/bin/sh
                [ "$1" = "--experimental_mnt" ] || exit 64
                [ "$2" = "new" ] || exit 64
                [ "$3" = "--help" ] || exit 64
                [ "$#" = "3" ] || exit 64
                printf '%s\\n' "Mount API to use: 'new' (fsopen/fsmount), 'old' (mount syscall), or" \
                    "'auto' (auto-detect based on kernel version). Default: 'old'"
                """);

        assertThat(NsJailLauncher.probeHelp(nsjail)).isTrue();
    }

    @Test
    void rejectsExecutableWhoseHelpDoesNotSupportNewMountApi() throws IOException {
        Path nsjail = executable("nsjail-old-mount", """
                #!/bin/sh
                [ "$1" = "--help" ] && exit 0
                exit 64
                """);

        assertThat(NsJailLauncher.probeHelp(nsjail)).isFalse();
    }

    @Test
    void rejectsExecutableWhoseHelpCommandFails() throws IOException {
        Path nsjail = executable("nsjail-fail", """
                #!/bin/sh
                [ "$1" = "--experimental_mnt" ] || exit 64
                [ "$2" = "new" ] || exit 64
                [ "$3" = "--help" ] || exit 64
                exit 9
                """);

        assertThat(NsJailLauncher.probeHelp(nsjail)).isFalse();
    }

    @Test
    void rejectsExecutableWhoseProbeTimesOut() throws IOException {
        Path nsjail = executable("nsjail-timeout", "#!/bin/sh\nsleep 10\n");

        assertThat(NsJailLauncher.probeHelp(nsjail, 100)).isFalse();
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

package com.oj.runner.execution.linux;

import com.oj.runner.config.LinuxSandboxProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SandboxWorkspaceManagerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsPrivateUniqueWorkspaceAndCleansIt() throws Exception {
        SandboxWorkspaceManager manager = manager();
        SandboxWorkspace first = manager.create("11111111-1111-4111-8111-111111111111");
        SandboxWorkspace second = manager.create("11111111-1111-4111-8111-111111111111");

        assertThat(first.root()).isNotEqualTo(second.root());
        assertThat(Files.getPosixFilePermissions(first.root()))
                .isEqualTo(PosixFilePermissions.fromString("rwx------"));
        manager.cleanup(first);
        manager.cleanup(second);
        assertThat(temporaryDirectory.resolve("jobs")).isEmptyDirectory();
    }

    @Test
    void cleanupNeverFollowsStudentCreatedSymbolicLinks() throws Exception {
        SandboxWorkspaceManager manager = manager();
        SandboxWorkspace workspace = manager.create("11111111-1111-4111-8111-111111111111");
        Path outside = Files.writeString(temporaryDirectory.resolve("outside-secret"), "preserve");
        Files.createSymbolicLink(workspace.files().resolve("escape"), outside);

        manager.cleanup(workspace);

        assertThat(outside).exists().hasContent("preserve");
    }

    @Test
    void sourceFilenameCannotEscapeWorkspace() throws Exception {
        SandboxWorkspaceManager manager = manager();
        SandboxWorkspace workspace = manager.create("11111111-1111-4111-8111-111111111111");
        assertThatThrownBy(() -> manager.writeSource(workspace, "../Main.py", "print(1)"))
                .isInstanceOf(IllegalArgumentException.class);
        manager.cleanup(workspace);
    }

    private SandboxWorkspaceManager manager() throws Exception {
        Path root = temporaryDirectory.resolve("jobs");
        Files.createDirectories(root);
        LinuxSandboxProperties properties = new LinuxSandboxProperties();
        properties.setWorkspaceRoot(root.toString());
        return new SandboxWorkspaceManager(properties);
    }
}

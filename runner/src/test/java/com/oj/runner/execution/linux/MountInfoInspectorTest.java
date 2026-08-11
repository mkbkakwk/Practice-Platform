package com.oj.runner.execution.linux;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.oj.runner.execution.linux.MountInfoInspector.ReadOnlyStatus.INVALID;
import static com.oj.runner.execution.linux.MountInfoInspector.ReadOnlyStatus.READ_ONLY;
import static com.oj.runner.execution.linux.MountInfoInspector.ReadOnlyStatus.READ_WRITE;
import static com.oj.runner.execution.linux.MountInfoInspector.ReadOnlyStatus.TARGET_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;

class MountInfoInspectorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void exactReadOnlyMountPasses() throws Exception {
        Path rootfs = Files.createDirectory(temporaryDirectory.resolve("rootfs"));

        assertThat(MountInfoInspector.inspectReadOnly(rootfs, mount(rootfs, "ro,relatime")))
                .isEqualTo(READ_ONLY);
    }

    @Test
    void readOnlyParentMountPasses() throws Exception {
        Path parent = Files.createDirectory(temporaryDirectory.resolve("parent"));
        Path rootfs = Files.createDirectory(parent.resolve("rootfs"));

        assertThat(MountInfoInspector.inspectReadOnly(rootfs, mount(parent, "ro,nosuid")))
                .isEqualTo(READ_ONLY);
    }

    @Test
    void moreSpecificReadWriteMountFails() throws Exception {
        Path parent = Files.createDirectory(temporaryDirectory.resolve("parent"));
        Path rootfs = Files.createDirectory(parent.resolve("rootfs"));
        String mountInfo = mount(parent, "ro,relatime") + mount(rootfs, "rw,relatime");

        assertThat(MountInfoInspector.inspectReadOnly(rootfs, mountInfo)).isEqualTo(READ_WRITE);
    }

    @Test
    void malformedMountInfoFailsClosed() throws Exception {
        Path rootfs = Files.createDirectory(temporaryDirectory.resolve("rootfs"));

        assertThat(MountInfoInspector.inspectReadOnly(rootfs, "not mountinfo\n")).isEqualTo(INVALID);
    }

    @Test
    void escapedMountPointIsDecodedBeforeMatching() throws Exception {
        Path rootfs = Files.createDirectory(temporaryDirectory.resolve("runtime rootfs"));
        String escaped = rootfs.toString().replace(" ", "\\040");

        assertThat(MountInfoInspector.inspectReadOnly(rootfs, mount(escaped, "ro,relatime")))
                .isEqualTo(READ_ONLY);
    }

    @Test
    void missingRootfsFailsClosed() {
        Path missing = temporaryDirectory.resolve("missing");

        assertThat(MountInfoInspector.inspectReadOnly(missing, mount(temporaryDirectory, "ro")))
                .isEqualTo(TARGET_UNAVAILABLE);
    }

    private String mount(Path mountPoint, String options) {
        return mount(mountPoint.toString(), options);
    }

    private String mount(String mountPoint, String options) {
        return "24 20 0:21 / " + mountPoint + " " + options + " - ext4 /dev/root rw\n";
    }
}

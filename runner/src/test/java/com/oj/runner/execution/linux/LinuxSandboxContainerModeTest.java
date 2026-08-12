package com.oj.runner.execution.linux;

import com.oj.runner.config.LinuxSandboxProperties;
import com.oj.runner.language.LanguageProfileRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LinuxSandboxContainerModeTest {

    @Test
    void ordinaryContainerRemainsUnsupportedByDefault() {
        LinuxSandboxPreflight preflight = preflight(false);
        var failures = new ArrayList<String>();

        preflight.inspectContainerMode(failures, true);

        assertThat(failures).containsExactly("container-host-unsupported");
    }

    @Test
    void explicitlyConfiguredContainerIsAcceptedForTheRemainingSecurityChecks() {
        LinuxSandboxPreflight preflight = preflight(true);
        var failures = new ArrayList<String>();

        preflight.inspectContainerMode(failures, true);

        assertThat(failures).isEmpty();
    }

    @Test
    void containerModeFailsClosedOutsideAContainer() {
        LinuxSandboxPreflight preflight = preflight(true);
        var failures = new ArrayList<String>();

        preflight.inspectContainerMode(failures, false);

        assertThat(failures).containsExactly("container-marker-missing");
    }

    private LinuxSandboxPreflight preflight(boolean containerized) {
        LinuxSandboxProperties properties = new LinuxSandboxProperties();
        properties.setContainerized(containerized);
        return new LinuxSandboxPreflight(
                properties,
                new LanguageProfileRegistry(),
                mock(SandboxWorkspaceManager.class),
                mock(NsJailConfigWriter.class),
                mock(ExecutionCgroupManager.class),
                new LanguageCommandResolver(),
                mock(SandboxProcessLauncher.class),
                mock(NamespaceIsolationVerifier.class));
    }
}

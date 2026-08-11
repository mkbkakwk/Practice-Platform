package com.oj.runner.execution.linux;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NamespaceIsolationVerifierTest {

    private final NamespaceIsolationVerifier verifier = new NamespaceIsolationVerifier();

    @Test
    void allEightDistinctNamespacesAndPidOnePass() {
        Map<String, Long> runner = identities(1_000);
        Map<String, Long> sandbox = identities(2_000);

        NamespaceIsolationVerifier.Verification result = verifier.verify(
                output(sandbox, 1), namespace -> target(namespace, runner.get(namespace)));

        assertThat(result.failures()).isEmpty();
        assertThat(result.runnerNamespaces()).containsExactlyInAnyOrderEntriesOf(runner);
        assertThat(result.sandboxNamespaces()).containsExactlyInAnyOrderEntriesOf(sandbox);
        assertThat(result.sandboxPid()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"mnt", "pid", "net", "uts", "ipc", "user", "cgroup", "time"})
    void eachSharedNamespaceFailsClosedWithAnExplicitCheck(String sharedNamespace) {
        Map<String, Long> runner = identities(1_000);
        Map<String, Long> sandbox = identities(2_000);
        sandbox.put(sharedNamespace, runner.get(sharedNamespace));

        NamespaceIsolationVerifier.Verification result = verifier.verify(
                output(sandbox, 1), namespace -> target(namespace, runner.get(namespace)));

        assertThat(result.failures()).contains("namespace-" + sharedNamespace + "-not-isolated");
    }

    @Test
    void missingAndMalformedSandboxIdentitiesFailClosed() {
        Map<String, Long> sandbox = identities(2_000);
        sandbox.remove("time");
        String output = output(sandbox, 1) + "NS\tpid\tnot-an-inode\n";

        NamespaceIsolationVerifier.Verification result = verifier.verify(
                output, namespace -> target(namespace, identities(1_000).get(namespace)));

        assertThat(result.failures())
                .contains("namespace-time-sandbox-unavailable", "namespace-probe-output-invalid");
    }

    @Test
    void unreadableAndMalformedRunnerIdentitiesFailClosed() {
        NamespaceIsolationVerifier.Verification result = verifier.verify(
                output(identities(2_000), 1), namespace -> {
                    if (namespace.equals("time")) {
                        throw new IOException("unreadable");
                    }
                    if (namespace.equals("pid")) {
                        return "pid:not-an-inode";
                    }
                    return target(namespace, identities(1_000).get(namespace));
                });

        assertThat(result.failures())
                .contains("namespace-time-runner-unavailable", "namespace-pid-runner-unavailable");
    }

    @Test
    void studentMustBePidOne() {
        NamespaceIsolationVerifier.Verification result = verifier.verify(
                output(identities(2_000), 2),
                namespace -> target(namespace, identities(1_000).get(namespace)));

        assertThat(result.failures()).contains("namespace-pid-not-init");
    }

    @Test
    void missingOrMalformedPidFailsClosed() {
        NamespaceIsolationVerifier.Verification missing = verifier.verify(
                output(identities(2_000), null),
                namespace -> target(namespace, identities(1_000).get(namespace)));
        NamespaceIsolationVerifier.Verification malformed = verifier.verify(
                output(identities(2_000), null) + "PID\tnot-a-number\n",
                namespace -> target(namespace, identities(1_000).get(namespace)));

        assertThat(missing.failures()).contains("namespace-pid-not-init");
        assertThat(malformed.failures())
                .contains("namespace-pid-not-init", "namespace-probe-output-invalid");
    }

    @Test
    void probeSourceReadsEveryRequiredNamespaceAndPid() {
        String source = NamespaceIsolationVerifier.pythonProbeSource();

        for (String namespace : NamespaceIsolationVerifier.REQUIRED_NAMESPACES) {
            assertThat(source).contains("'" + namespace + "'");
        }
        assertThat(source).contains("/proc/self/ns/", "os.getpid()");
    }

    private Map<String, Long> identities(long firstInode) {
        Map<String, Long> identities = new LinkedHashMap<>();
        long inode = firstInode;
        for (String namespace : NamespaceIsolationVerifier.REQUIRED_NAMESPACES) {
            identities.put(namespace, inode++);
        }
        return identities;
    }

    private String output(Map<String, Long> identities, Integer pid) {
        StringBuilder output = new StringBuilder();
        for (String namespace : NamespaceIsolationVerifier.REQUIRED_NAMESPACES) {
            Long inode = identities.get(namespace);
            if (inode != null) {
                output.append("NS\t").append(namespace).append('\t')
                        .append(target(namespace, inode)).append('\n');
            }
        }
        if (pid != null) {
            output.append("PID\t").append(pid).append('\n');
        }
        return output.toString();
    }

    private String target(String namespace, long inode) {
        return namespace + ":[" + inode + "]";
    }
}

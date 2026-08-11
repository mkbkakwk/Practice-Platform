package com.oj.runner.execution.linux;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NsJailLauncherClassificationTest {

    private static final long LIMIT = 64L * 1024 * 1024;

    @Test
    void oomAndOomKillEventsAreAuthoritativeMemoryEvidence() {
        assertThat(classify(7, snapshot(1_024, 0, 1, 0)))
                .isEqualTo(SandboxTermination.MEMORY_LIMIT);
        assertThat(classify(7, snapshot(1_024, 0, 0, 1)))
                .isEqualTo(SandboxTermination.MEMORY_LIMIT);
    }

    @Test
    void maxEventNearLimitRequiresAbnormalExitAndOrdinaryErrorsStayRuntimeErrors() {
        assertThat(classify(7, snapshot(LIMIT, 1, 0, 0)))
                .isEqualTo(SandboxTermination.MEMORY_LIMIT);
        assertThat(classify(7, snapshot(1_024, 0, 0, 0)))
                .isEqualTo(SandboxTermination.COMPLETED);
        assertThat(classify(0, snapshot(LIMIT, 1, 0, 0)))
                .isEqualTo(SandboxTermination.COMPLETED);
    }

    @Test
    void forcedAndOutputTerminationsRemainHigherPriorityThanMemoryEvidence() {
        ExecutionCgroupSnapshot memoryEvidence = snapshot(LIMIT, 2, 1, 1);
        assertThat(NsJailLauncher.classifyTermination(
                SandboxTermination.TIME_LIMIT, false, 137, memoryEvidence, LIMIT))
                .isEqualTo(SandboxTermination.TIME_LIMIT);
        assertThat(NsJailLauncher.classifyTermination(
                SandboxTermination.OUTPUT_LIMIT, false, 137, memoryEvidence, LIMIT))
                .isEqualTo(SandboxTermination.OUTPUT_LIMIT);
        assertThat(NsJailLauncher.classifyTermination(
                SandboxTermination.WORKSPACE_LIMIT, false, 137, memoryEvidence, LIMIT))
                .isEqualTo(SandboxTermination.WORKSPACE_LIMIT);
        assertThat(NsJailLauncher.classifyTermination(
                null, true, 137, memoryEvidence, LIMIT))
                .isEqualTo(SandboxTermination.OUTPUT_LIMIT);
    }

    private SandboxTermination classify(int exitCode, ExecutionCgroupSnapshot snapshot) {
        return NsJailLauncher.classifyTermination(null, false, exitCode, snapshot, LIMIT);
    }

    private ExecutionCgroupSnapshot snapshot(long peak, long max, long oom, long oomKill) {
        return new ExecutionCgroupSnapshot(peak, max, oom, oomKill, 0);
    }
}

package com.oj.runner.execution.docker;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DockerMemoryEvidenceTest {

    @Test
    void classifiesContainerOomState() {
        assertThat(DockerMemoryEvidence.stateOnly(true).exceededFor(137)).isTrue();
    }

    @Test
    void classifiesLimitHitCounterWithAbnormalExit() {
        assertThat(new DockerMemoryEvidence(false, 32, 1, 1).exceededFor(9)).isTrue();
    }

    @Test
    void classifiesAbnormalExitAtNinetyFivePercentOfLimit() {
        assertThat(new DockerMemoryEvidence(false, 100, 95, 0).exceededFor(137)).isTrue();
    }

    @Test
    void doesNotGuessMemoryLimitFromExitCodeAlone() {
        assertThat(DockerMemoryEvidence.stateOnly(false).exceededFor(137)).isFalse();
        assertThat(new DockerMemoryEvidence(false, 100, 20, 0).exceededFor(1)).isFalse();
    }

    @Test
    void normalExitIsNotMemoryLimitEvenAfterARecoverableAllocationFailure() {
        assertThat(new DockerMemoryEvidence(false, 100, 100, 1).exceededFor(0)).isFalse();
    }
}

package com.oj.contest;

import com.oj.entity.ContestEntity;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ContestLifecycleTest {
    private static final Instant START = Instant.parse("2026-08-13T10:00:00Z");
    private static final Instant END = Instant.parse("2026-08-13T12:00:00Z");

    @Test
    void persistentStateAndClockDeriveEveryPhase() {
        ContestEntity contest = contest("DRAFT");
        assertThat(ContestLifecycle.phase(contest, fixed(START))).isEqualTo(ContestPhase.DRAFT);

        contest.setStatus("PUBLISHED");
        assertThat(ContestLifecycle.phase(contest, fixed(START.minusMillis(1)))).isEqualTo(ContestPhase.UPCOMING);
        assertThat(ContestLifecycle.phase(contest, fixed(START))).isEqualTo(ContestPhase.RUNNING);
        assertThat(ContestLifecycle.phase(contest, fixed(END.minusMillis(1)))).isEqualTo(ContestPhase.RUNNING);
        assertThat(ContestLifecycle.phase(contest, fixed(END))).isEqualTo(ContestPhase.ENDED);

        contest.setStatus("CANCELLED");
        assertThat(ContestLifecycle.phase(contest, fixed(START))).isEqualTo(ContestPhase.CANCELLED);
    }

    @Test
    void submissionWindowIsClosedOpen() {
        ContestEntity contest = contest("PUBLISHED");
        assertThat(ContestLifecycle.acceptsSubmissions(contest, START.minusMillis(1))).isFalse();
        assertThat(ContestLifecycle.acceptsSubmissions(contest, START)).isTrue();
        assertThat(ContestLifecycle.acceptsSubmissions(contest, END.minusMillis(1))).isTrue();
        assertThat(ContestLifecycle.acceptsSubmissions(contest, END)).isFalse();
        assertThat(ContestLifecycle.acceptsSubmissions(contest, END.plusMillis(1))).isFalse();
    }

    private ContestEntity contest(String status) {
        ContestEntity contest = new ContestEntity();
        contest.setStatus(status);
        contest.setStartAt(START);
        contest.setEndAt(END);
        return contest;
    }

    private Clock fixed(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }
}

package com.oj.contest;

import com.oj.entity.ContestEntity;

import java.time.Clock;
import java.time.Instant;

public final class ContestLifecycle {
    private ContestLifecycle() {}

    public static ContestPhase phase(ContestEntity contest, Clock clock) {
        return phase(contest, clock.instant());
    }

    public static ContestPhase phase(ContestEntity contest, Instant now) {
        ContestStatus status = ContestStatus.valueOf(contest.getStatus());
        if (status == ContestStatus.DRAFT) return ContestPhase.DRAFT;
        if (status == ContestStatus.CANCELLED) return ContestPhase.CANCELLED;
        if (now.isBefore(contest.getStartAt())) return ContestPhase.UPCOMING;
        if (now.isBefore(contest.getEndAt())) return ContestPhase.RUNNING;
        return ContestPhase.ENDED;
    }

    public static boolean acceptsSubmissions(ContestEntity contest, Instant now) {
        return ContestStatus.PUBLISHED.name().equals(contest.getStatus())
                && !now.isBefore(contest.getStartAt())
                && now.isBefore(contest.getEndAt());
    }
}

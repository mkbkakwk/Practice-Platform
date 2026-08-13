package com.oj.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public final class ContestDtos {
    private ContestDtos() {}

    public record Summary(
            Integer id, String title, String description, String status, String phase,
            String accessType, Integer ownerId, String ownerUsername,
            Instant startAt, Instant endAt, boolean participant,
            Instant createdAt, Instant updatedAt) {}

    public record Detail(Summary contest, List<ProblemItem> problems) {}

    public record ProblemItem(
            Long contestProblemId, String problemType, Integer problemId,
            Integer displayOrder, String label, String title, String difficulty,
            String slug, Object content) {}

    public record Participant(
            Long id, Integer userId, String username, Integer addedBy, Instant joinedAt) {}

    public record OfficeSubmission(
            Integer id, Integer exerciseId, Long contestProblemId, String studentDocName,
            String status, Integer score, String judgeVersion, Object resultDetail, LocalDateTime judgedAt) {}
}

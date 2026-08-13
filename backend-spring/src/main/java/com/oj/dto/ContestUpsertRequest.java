package com.oj.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public class ContestUpsertRequest {
    @NotBlank @Size(max = 120)
    private String title;
    @Size(max = 20000)
    private String description = "";
    @NotNull
    private Instant startAt;
    @NotNull
    private Instant endAt;
    @NotBlank @Pattern(regexp = "OPEN|INVITE_ONLY", message = "比赛访问模式无效")
    private String accessType = "OPEN";

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getStartAt() { return startAt; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }
    public Instant getEndAt() { return endAt; }
    public void setEndAt(Instant endAt) { this.endAt = endAt; }
    public String getAccessType() { return accessType; }
    public void setAccessType(String accessType) { this.accessType = accessType; }
}

package com.oj.dto;

import jakarta.validation.constraints.NotNull;

public class ContestParticipantRequest {
    @NotNull
    private Integer userId;

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
}

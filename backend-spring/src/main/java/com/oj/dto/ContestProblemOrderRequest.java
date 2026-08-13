package com.oj.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public class ContestProblemOrderRequest {
    @NotEmpty @Size(max = 100)
    private List<Long> contestProblemIds;

    public List<Long> getContestProblemIds() { return contestProblemIds; }
    public void setContestProblemIds(List<Long> contestProblemIds) { this.contestProblemIds = contestProblemIds; }
}

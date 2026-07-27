package com.oj.dto;

import jakarta.validation.constraints.NotNull;

public class ReviewRequest {
    @NotNull
    private Integer score;   // 0-100
    private String comment;
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}

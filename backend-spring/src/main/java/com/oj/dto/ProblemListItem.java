package com.oj.dto;

import java.time.LocalDateTime;

public class ProblemListItem {
    private Integer id;
    private String slug;
    private String title;
    private String difficulty;
    private String[] tags;
    private Integer timeLimit;
    private Integer memoryLimit;
    private Boolean visible;
    private Integer createdBy;
    private String creatorUsername;
    private Long submissionCount;
    private LocalDateTime createdAt;

    public ProblemListItem() {}

    public ProblemListItem(Integer id, String slug, String title, String difficulty, String[] tags,
                           Integer timeLimit, Integer memoryLimit, Boolean visible,
                           Integer createdBy, String creatorUsername, Long submissionCount,
                           LocalDateTime createdAt) {
        this.id = id;
        this.slug = slug;
        this.title = title;
        this.difficulty = difficulty;
        this.tags = tags;
        this.timeLimit = timeLimit;
        this.memoryLimit = memoryLimit;
        this.visible = visible;
        this.createdBy = createdBy;
        this.creatorUsername = creatorUsername;
        this.submissionCount = submissionCount;
        this.createdAt = createdAt;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String[] getTags() { return tags; }
    public void setTags(String[] tags) { this.tags = tags; }
    public Integer getTimeLimit() { return timeLimit; }
    public void setTimeLimit(Integer timeLimit) { this.timeLimit = timeLimit; }
    public Integer getMemoryLimit() { return memoryLimit; }
    public void setMemoryLimit(Integer memoryLimit) { this.memoryLimit = memoryLimit; }
    public Boolean getVisible() { return visible; }
    public void setVisible(Boolean visible) { this.visible = visible; }
    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
    public String getCreatorUsername() { return creatorUsername; }
    public void setCreatorUsername(String creatorUsername) { this.creatorUsername = creatorUsername; }
    public Long getSubmissionCount() { return submissionCount; }
    public void setSubmissionCount(Long submissionCount) { this.submissionCount = submissionCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

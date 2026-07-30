package com.oj.dto;

import java.time.LocalDateTime;

/** Lightweight view for list and management screens. Does not expose the answer. */
public class OfficeQuestionListItem {
    private Integer id;
    private String appType;
    private String category;
    private String difficulty;
    private String questionType;
    private String content;
    private Boolean visible;
    private Integer createdBy;
    private String creatorUsername;
    private Long submissionCount;
    private LocalDateTime createdAt;

    public OfficeQuestionListItem() {}

    public OfficeQuestionListItem(Integer id, String appType, String category, String difficulty,
                                  String questionType, String content, Boolean visible,
                                  Integer createdBy, String creatorUsername, Long submissionCount,
                                  LocalDateTime createdAt) {
        this.id = id;
        this.appType = appType;
        this.category = category;
        this.difficulty = difficulty;
        this.questionType = questionType;
        this.content = content;
        this.visible = visible;
        this.createdBy = createdBy;
        this.creatorUsername = creatorUsername;
        this.submissionCount = submissionCount;
        this.createdAt = createdAt;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getAppType() { return appType; }
    public void setAppType(String appType) { this.appType = appType; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
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

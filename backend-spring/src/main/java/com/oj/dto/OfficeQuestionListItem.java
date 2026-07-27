package com.oj.dto;

import java.util.List;

/** Lightweight view for the question list. Does NOT include options/answer. */
public class OfficeQuestionListItem {
    private Integer id;
    private String appType;
    private String category;
    private String difficulty;
    private String questionType;
    private String content;
    private Boolean visible;
    public OfficeQuestionListItem() {}
    public OfficeQuestionListItem(Integer id, String appType, String category, String difficulty,
                                  String questionType, String content, Boolean visible) {
        this.id = id; this.appType = appType; this.category = category; this.difficulty = difficulty;
        this.questionType = questionType; this.content = content; this.visible = visible;
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
}

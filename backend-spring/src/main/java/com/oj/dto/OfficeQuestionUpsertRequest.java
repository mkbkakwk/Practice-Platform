package com.oj.dto;

import jakarta.validation.constraints.NotBlank;

public class OfficeQuestionUpsertRequest {
    @NotBlank
    private String appType;       // WORD / EXCEL / PPT
    @NotBlank
    private String category;
    private String difficulty;    // defaults EASY
    @NotBlank
    private String questionType;  // SINGLE_CHOICE / MULTI_CHOICE / TRUE_FALSE
    @NotBlank
    private String content;
    private java.util.List<String> options;
    @NotBlank
    private String answer;
    private String explanation;
    private Boolean visible;
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
    public java.util.List<String> getOptions() { return options; }
    public void setOptions(java.util.List<String> options) { this.options = options; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public Boolean getVisible() { return visible; }
    public void setVisible(Boolean visible) { this.visible = visible; }
}

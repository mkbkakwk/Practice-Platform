package com.oj.dto;

import java.util.List;

/** Detail view for practicing. Includes options but NOT the answer. */
public class OfficeQuestionDetail {
    private Integer id;
    private String appType;
    private String category;
    private String difficulty;
    private String questionType;
    private String content;
    private List<String> options;
    // adminEdit: when true, answer/explanation are filled (for admin editing only).
    private String answer;
    private String explanation;
    private Boolean visible;
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
    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public Boolean getVisible() { return visible; }
    public void setVisible(Boolean visible) { this.visible = visible; }
}

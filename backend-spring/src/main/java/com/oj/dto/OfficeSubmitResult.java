package com.oj.dto;

public class OfficeSubmitResult {
    private Boolean correct;
    private String correctAnswer;
    private String explanation;
    public OfficeSubmitResult() {}
    public OfficeSubmitResult(Boolean correct, String correctAnswer, String explanation) {
        this.correct = correct; this.correctAnswer = correctAnswer; this.explanation = explanation;
    }
    public Boolean getCorrect() { return correct; }
    public void setCorrect(Boolean correct) { this.correct = correct; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
}

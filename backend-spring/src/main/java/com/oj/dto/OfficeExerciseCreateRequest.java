package com.oj.dto;

import jakarta.validation.constraints.NotBlank;

public class OfficeExerciseCreateRequest {
    @NotBlank
    private String title;
    private String difficulty;
    @NotBlank
    private String description;
    private Boolean visible;
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getVisible() { return visible; }
    public void setVisible(Boolean visible) { this.visible = visible; }
}

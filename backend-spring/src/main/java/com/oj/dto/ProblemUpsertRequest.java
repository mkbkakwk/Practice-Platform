package com.oj.dto;

import jakarta.validation.constraints.*;

public class ProblemUpsertRequest {
    @NotBlank
    @Size(min = 2, max = 60)
    @jakarta.validation.constraints.Pattern(regexp = "[a-z0-9-]+", message = "slug 只能包含小写字母、数字、连字符")
    private String slug;
    @NotBlank
    @Size(max = 120)
    private String title;
    @NotBlank
    private String description;
    private String inputFmt = "";
    private String outputFmt = "";
    private String difficulty = "EASY";
    @Min(100) @Max(30000)
    private Integer timeLimit = 1000;
    @Min(32) @Max(1024)
    private Integer memoryLimit = 256;
    private String[] tags = {};
    private Object samples;   // JSON array of {input,output}
    private Object testCases; // JSON array of {input,output}
    private Boolean visible = true;
    @jakarta.validation.constraints.Pattern(regexp = "PUBLIC|CONTEST_ONLY", message = "可见范围无效")
    private String contentVisibility = "PUBLIC";

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getInputFmt() { return inputFmt; }
    public void setInputFmt(String inputFmt) { this.inputFmt = inputFmt; }
    public String getOutputFmt() { return outputFmt; }
    public void setOutputFmt(String outputFmt) { this.outputFmt = outputFmt; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public Integer getTimeLimit() { return timeLimit; }
    public void setTimeLimit(Integer timeLimit) { this.timeLimit = timeLimit; }
    public Integer getMemoryLimit() { return memoryLimit; }
    public void setMemoryLimit(Integer memoryLimit) { this.memoryLimit = memoryLimit; }
    public String[] getTags() { return tags; }
    public void setTags(String[] tags) { this.tags = tags; }
    public Object getSamples() { return samples; }
    public void setSamples(Object samples) { this.samples = samples; }
    public Object getTestCases() { return testCases; }
    public void setTestCases(Object testCases) { this.testCases = testCases; }
    public Boolean getVisible() { return visible; }
    public void setVisible(Boolean visible) { this.visible = visible; }
    public String getContentVisibility() { return contentVisibility; }
    public void setContentVisibility(String contentVisibility) { this.contentVisibility = contentVisibility; }
}

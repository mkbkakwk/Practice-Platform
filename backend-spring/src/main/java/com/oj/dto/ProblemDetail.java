package com.oj.dto;

public class ProblemDetail {
    private Integer id;
    private String slug;
    private String title;
    private String description;
    private String inputFmt;
    private String outputFmt;
    private String difficulty;
    private String[] tags;
    private Integer timeLimit;
    private Integer memoryLimit;
    private Object samples; // parsed array of {input,output}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
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
    public String[] getTags() { return tags; }
    public void setTags(String[] tags) { this.tags = tags; }
    public Integer getTimeLimit() { return timeLimit; }
    public void setTimeLimit(Integer timeLimit) { this.timeLimit = timeLimit; }
    public Integer getMemoryLimit() { return memoryLimit; }
    public void setMemoryLimit(Integer memoryLimit) { this.memoryLimit = memoryLimit; }
    public Object getSamples() { return samples; }
    public void setSamples(Object samples) { this.samples = samples; }
}

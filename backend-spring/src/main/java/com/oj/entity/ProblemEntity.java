package com.oj.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.oj.common.StringArrayTypeHandler;

import java.time.LocalDateTime;

@TableName(value = "\"Problem\"", autoResultMap = true)
public class ProblemEntity {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String slug;
    private String title;
    private String description;
    private String inputFmt;
    private String outputFmt;
    private String difficulty;
    private Integer timeLimit;
    private Integer memoryLimit;
    @TableField(typeHandler = StringArrayTypeHandler.class)
    private String[] tags;
    private String samples;
    private String testCases;
    private Boolean visible;
    private String contentVisibility;
    private Integer createdBy;
    @TableField(exist = false)
    private String creatorUsername;
    private LocalDateTime createdAt;

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
    public Integer getTimeLimit() { return timeLimit; }
    public void setTimeLimit(Integer timeLimit) { this.timeLimit = timeLimit; }
    public Integer getMemoryLimit() { return memoryLimit; }
    public void setMemoryLimit(Integer memoryLimit) { this.memoryLimit = memoryLimit; }
    public String[] getTags() { return tags; }
    public void setTags(String[] tags) { this.tags = tags; }
    public String getSamples() { return samples; }
    public void setSamples(String samples) { this.samples = samples; }
    public String getTestCases() { return testCases; }
    public void setTestCases(String testCases) { this.testCases = testCases; }
    public Boolean getVisible() { return visible; }
    public void setVisible(Boolean visible) { this.visible = visible; }
    public String getContentVisibility() { return contentVisibility; }
    public void setContentVisibility(String contentVisibility) { this.contentVisibility = contentVisibility; }
    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
    public String getCreatorUsername() { return creatorUsername; }
    public void setCreatorUsername(String creatorUsername) { this.creatorUsername = creatorUsername; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

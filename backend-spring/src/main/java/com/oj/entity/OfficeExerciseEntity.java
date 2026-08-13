package com.oj.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("\"OfficeExercise\"")
public class OfficeExerciseEntity {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String title;
    private String difficulty;
    private String description;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String teacherDocPath;
    private String teacherDocName;
    private Boolean visible;
    private Integer createdBy;
    @TableField(exist = false)
    private String creatorUsername;
    private LocalDateTime createdAt;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTeacherDocPath() { return teacherDocPath; }
    public void setTeacherDocPath(String teacherDocPath) { this.teacherDocPath = teacherDocPath; }
    public String getTeacherDocName() { return teacherDocName; }
    public void setTeacherDocName(String teacherDocName) { this.teacherDocName = teacherDocName; }
    public Boolean getVisible() { return visible; }
    public void setVisible(Boolean visible) { this.visible = visible; }
    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
    public String getCreatorUsername() { return creatorUsername; }
    public void setCreatorUsername(String creatorUsername) { this.creatorUsername = creatorUsername; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

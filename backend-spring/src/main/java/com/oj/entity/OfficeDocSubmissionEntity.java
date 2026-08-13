package com.oj.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
import com.oj.common.MapJsonbTypeHandler;

@TableName(value = "\"OfficeDocSubmission\"", autoResultMap = true)
public class OfficeDocSubmissionEntity {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer userId;
    private Integer exerciseId;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String studentDocPath;
    private String studentDocName;
    private String autoResult;      // JSON
    private String compareResult;   // JSON
    private String status;          // AUTO_CHECKED / NEEDS_REVIEW / REVIEWED
    private Integer score;
    private String teacherComment;
    private String judgeVersion;
    @TableField(typeHandler = MapJsonbTypeHandler.class, jdbcType = org.apache.ibatis.type.JdbcType.OTHER)
    private java.util.Map<String, Object> resultDetail;
    private String errorCategory;
    private LocalDateTime judgedAt;
    private LocalDateTime createdAt;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public Integer getExerciseId() { return exerciseId; }
    public void setExerciseId(Integer exerciseId) { this.exerciseId = exerciseId; }
    public String getStudentDocPath() { return studentDocPath; }
    public void setStudentDocPath(String studentDocPath) { this.studentDocPath = studentDocPath; }
    public String getStudentDocName() { return studentDocName; }
    public void setStudentDocName(String studentDocName) { this.studentDocName = studentDocName; }
    public String getAutoResult() { return autoResult; }
    public void setAutoResult(String autoResult) { this.autoResult = autoResult; }
    public String getCompareResult() { return compareResult; }
    public void setCompareResult(String compareResult) { this.compareResult = compareResult; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getTeacherComment() { return teacherComment; }
    public void setTeacherComment(String teacherComment) { this.teacherComment = teacherComment; }
    public String getJudgeVersion() { return judgeVersion; }
    public void setJudgeVersion(String judgeVersion) { this.judgeVersion = judgeVersion; }
    public java.util.Map<String, Object> getResultDetail() { return resultDetail; }
    public void setResultDetail(java.util.Map<String, Object> resultDetail) { this.resultDetail = resultDetail; }
    public String getErrorCategory() { return errorCategory; }
    public void setErrorCategory(String errorCategory) { this.errorCategory = errorCategory; }
    public LocalDateTime getJudgedAt() { return judgedAt; }
    public void setJudgedAt(LocalDateTime judgedAt) { this.judgedAt = judgedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

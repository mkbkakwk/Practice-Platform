package com.oj.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("\"ContestProblem\"")
public class ContestProblemEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer contestId;
    private String problemType;
    private Integer algorithmProblemId;
    private Integer officeExerciseId;
    private Integer displayOrder;
    private String label;
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getContestId() { return contestId; }
    public void setContestId(Integer contestId) { this.contestId = contestId; }
    public String getProblemType() { return problemType; }
    public void setProblemType(String problemType) { this.problemType = problemType; }
    public Integer getAlgorithmProblemId() { return algorithmProblemId; }
    public void setAlgorithmProblemId(Integer algorithmProblemId) { this.algorithmProblemId = algorithmProblemId; }
    public Integer getOfficeExerciseId() { return officeExerciseId; }
    public void setOfficeExerciseId(Integer officeExerciseId) { this.officeExerciseId = officeExerciseId; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

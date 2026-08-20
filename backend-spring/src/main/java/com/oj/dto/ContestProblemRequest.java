package com.oj.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ContestProblemRequest {
    @NotBlank @Pattern(regexp = "ALGORITHM|OFFICE_CHOICE|OFFICE_DOCX", message = "比赛题型无效")
    private String problemType;
    @NotNull
    private Integer problemId;
    @Size(max = 40)
    private String label;

    public String getProblemType() { return problemType; }
    public void setProblemType(String problemType) { this.problemType = problemType; }
    public Integer getProblemId() { return problemId; }
    public void setProblemId(Integer problemId) { this.problemId = problemId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}

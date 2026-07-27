package com.oj.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public class OfficeSubmitRequest {
    @NotNull
    private Integer questionId;
    @NotNull
    private List<String> selected;  // e.g. ["0"] or ["0","2"] or ["T"]
    public Integer getQuestionId() { return questionId; }
    public void setQuestionId(Integer questionId) { this.questionId = questionId; }
    public List<String> getSelected() { return selected; }
    public void setSelected(List<String> selected) { this.selected = selected; }
}

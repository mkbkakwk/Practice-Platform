package com.oj.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public class ContestChoiceSubmitRequest {
    @NotNull
    private List<String> selected;

    public List<String> getSelected() { return selected; }
    public void setSelected(List<String> selected) { this.selected = selected; }
}

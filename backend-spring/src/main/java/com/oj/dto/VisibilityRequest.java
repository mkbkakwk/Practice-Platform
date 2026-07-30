package com.oj.dto;

import jakarta.validation.constraints.NotNull;

public class VisibilityRequest {
    @NotNull
    private Boolean visible;

    public Boolean getVisible() { return visible; }
    public void setVisible(Boolean visible) { this.visible = visible; }
}

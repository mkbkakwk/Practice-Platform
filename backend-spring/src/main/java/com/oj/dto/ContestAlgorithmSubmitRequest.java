package com.oj.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ContestAlgorithmSubmitRequest {
    @NotBlank
    private String language;
    @NotBlank @Size(max = 1000000)
    private String code;

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}

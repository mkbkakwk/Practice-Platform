package com.oj.runner.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "runner")
public class RunnerProperties {

    @NotBlank
    private String token;

    @Min(1)
    @Max(64)
    private int maxConcurrentJobs = 2;

    @Min(1)
    private int maxRequestBytes = 4_194_304;

    @Min(1)
    private int maxSourceBytes = 1_048_576;

    @Min(1)
    private int maxStdinBytes = 1_048_576;

    @Min(1)
    @Max(10_000)
    private int maxCases = 1_000;

    @Min(1)
    private long maxCompileTimeMs = 60_000;

    @Min(1)
    private long maxRunTimeMs = 10_000;

    @Min(1)
    private long maxMemoryMb = 2_048;

    @Min(1)
    private int maxOutputLimitBytes = 16_777_216;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getMaxConcurrentJobs() {
        return maxConcurrentJobs;
    }

    public void setMaxConcurrentJobs(int maxConcurrentJobs) {
        this.maxConcurrentJobs = maxConcurrentJobs;
    }

    public int getMaxRequestBytes() {
        return maxRequestBytes;
    }

    public void setMaxRequestBytes(int maxRequestBytes) {
        this.maxRequestBytes = maxRequestBytes;
    }

    public int getMaxSourceBytes() {
        return maxSourceBytes;
    }

    public void setMaxSourceBytes(int maxSourceBytes) {
        this.maxSourceBytes = maxSourceBytes;
    }

    public int getMaxStdinBytes() {
        return maxStdinBytes;
    }

    public void setMaxStdinBytes(int maxStdinBytes) {
        this.maxStdinBytes = maxStdinBytes;
    }

    public int getMaxCases() {
        return maxCases;
    }

    public void setMaxCases(int maxCases) {
        this.maxCases = maxCases;
    }

    public long getMaxCompileTimeMs() {
        return maxCompileTimeMs;
    }

    public void setMaxCompileTimeMs(long maxCompileTimeMs) {
        this.maxCompileTimeMs = maxCompileTimeMs;
    }

    public long getMaxRunTimeMs() {
        return maxRunTimeMs;
    }

    public void setMaxRunTimeMs(long maxRunTimeMs) {
        this.maxRunTimeMs = maxRunTimeMs;
    }

    public long getMaxMemoryMb() {
        return maxMemoryMb;
    }

    public void setMaxMemoryMb(long maxMemoryMb) {
        this.maxMemoryMb = maxMemoryMb;
    }

    public int getMaxOutputLimitBytes() {
        return maxOutputLimitBytes;
    }

    public void setMaxOutputLimitBytes(int maxOutputLimitBytes) {
        this.maxOutputLimitBytes = maxOutputLimitBytes;
    }
}

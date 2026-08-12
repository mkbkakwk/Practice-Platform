package com.oj.runner.config;

import com.oj.runner.api.RunnerLanguage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "runner.sandbox.docker")
public class DockerSandboxProperties {

    @NotBlank
    private String host = "unix:///var/run/docker.sock";
    @NotBlank
    private String instanceId = "runner-local";
    @NotBlank
    private String pythonImage = "practice-sandbox-python:local";
    @NotBlank
    private String javascriptImage = "practice-sandbox-javascript:local";
    @NotBlank
    private String cImage = "practice-sandbox-c:local";
    @NotBlank
    private String cppImage = "practice-sandbox-cpp17:local";
    @NotBlank
    private String javaImage = "practice-sandbox-java:local";
    @Min(1)
    @Max(512)
    private long pidsLimit = 64;
    @Min(1)
    private long nanoCpus = 1_000_000_000L;
    @Min(1_048_576)
    private long workspaceBytes = 67_108_864L;
    @Min(1_048_576)
    private long tmpBytes = 33_554_432L;
    @Min(1)
    @Max(10)
    private int cleanupRetries = 3;
    @Min(1_000)
    private long controlTimeoutMs = 10_000;

    public String imageFor(RunnerLanguage language) {
        return switch (language) {
            case PYTHON -> pythonImage;
            case JAVASCRIPT -> javascriptImage;
            case C -> cImage;
            case CPP17 -> cppImage;
            case JAVA -> javaImage;
        };
    }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    public String getPythonImage() { return pythonImage; }
    public void setPythonImage(String pythonImage) { this.pythonImage = pythonImage; }
    public String getJavascriptImage() { return javascriptImage; }
    public void setJavascriptImage(String javascriptImage) { this.javascriptImage = javascriptImage; }
    public String getCImage() { return cImage; }
    public void setCImage(String cImage) { this.cImage = cImage; }
    public String getCppImage() { return cppImage; }
    public void setCppImage(String cppImage) { this.cppImage = cppImage; }
    public String getJavaImage() { return javaImage; }
    public void setJavaImage(String javaImage) { this.javaImage = javaImage; }
    public long getPidsLimit() { return pidsLimit; }
    public void setPidsLimit(long pidsLimit) { this.pidsLimit = pidsLimit; }
    public long getNanoCpus() { return nanoCpus; }
    public void setNanoCpus(long nanoCpus) { this.nanoCpus = nanoCpus; }
    public long getWorkspaceBytes() { return workspaceBytes; }
    public void setWorkspaceBytes(long workspaceBytes) { this.workspaceBytes = workspaceBytes; }
    public long getTmpBytes() { return tmpBytes; }
    public void setTmpBytes(long tmpBytes) { this.tmpBytes = tmpBytes; }
    public int getCleanupRetries() { return cleanupRetries; }
    public void setCleanupRetries(int cleanupRetries) { this.cleanupRetries = cleanupRetries; }
    public long getControlTimeoutMs() { return controlTimeoutMs; }
    public void setControlTimeoutMs(long controlTimeoutMs) { this.controlTimeoutMs = controlTimeoutMs; }
}

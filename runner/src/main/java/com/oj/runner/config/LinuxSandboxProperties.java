package com.oj.runner.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "runner.sandbox")
public class LinuxSandboxProperties {

    @NotBlank private String mode = "unavailable";
    @NotBlank private String nsjailPath = "/usr/bin/nsjail";
    @NotBlank private String rootfs = "/srv/oj-sandbox-runner/rootfs";
    @NotBlank private String workspaceRoot = "/run/oj-sandbox-runner/jobs";
    @NotBlank private String seccompPolicy = "/etc/oj-sandbox-runner/nsjail-seccomp.policy";
    @NotBlank private String cgroupV2Mount = "/sys/fs/cgroup/system.slice/oj-sandbox-runner.service";
    @Min(1) private int studentUid = 65534;
    @Min(1) private int studentGid = 65534;
    @Min(4) @Max(256) private int pidsMax = 32;
    @Min(1) @Max(1000) private int cpuMsPerSecond = 1000;
    @Min(1) private long tmpfsBytes = 67_108_864;
    @Min(1) private long workspaceBytes = 134_217_728;
    @Min(1) private int workspaceFiles = 4096;
    @Min(1) private long maxFileBytes = 67_108_864;
    @Min(16) @Max(1024) private int maxOpenFiles = 64;
    @Min(0) @Max(10_000) private long outerTimeoutGraceMs = 1000;
    @Min(1) @Max(1000) private long workspacePollMs = 25;

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getNsjailPath() { return nsjailPath; }
    public void setNsjailPath(String nsjailPath) { this.nsjailPath = nsjailPath; }
    public String getRootfs() { return rootfs; }
    public void setRootfs(String rootfs) { this.rootfs = rootfs; }
    public String getWorkspaceRoot() { return workspaceRoot; }
    public void setWorkspaceRoot(String workspaceRoot) { this.workspaceRoot = workspaceRoot; }
    public String getSeccompPolicy() { return seccompPolicy; }
    public void setSeccompPolicy(String seccompPolicy) { this.seccompPolicy = seccompPolicy; }
    public String getCgroupV2Mount() { return cgroupV2Mount; }
    public void setCgroupV2Mount(String cgroupV2Mount) { this.cgroupV2Mount = cgroupV2Mount; }
    public int getStudentUid() { return studentUid; }
    public void setStudentUid(int studentUid) { this.studentUid = studentUid; }
    public int getStudentGid() { return studentGid; }
    public void setStudentGid(int studentGid) { this.studentGid = studentGid; }
    public int getPidsMax() { return pidsMax; }
    public void setPidsMax(int pidsMax) { this.pidsMax = pidsMax; }
    public int getCpuMsPerSecond() { return cpuMsPerSecond; }
    public void setCpuMsPerSecond(int cpuMsPerSecond) { this.cpuMsPerSecond = cpuMsPerSecond; }
    public long getTmpfsBytes() { return tmpfsBytes; }
    public void setTmpfsBytes(long tmpfsBytes) { this.tmpfsBytes = tmpfsBytes; }
    public long getWorkspaceBytes() { return workspaceBytes; }
    public void setWorkspaceBytes(long workspaceBytes) { this.workspaceBytes = workspaceBytes; }
    public int getWorkspaceFiles() { return workspaceFiles; }
    public void setWorkspaceFiles(int workspaceFiles) { this.workspaceFiles = workspaceFiles; }
    public long getMaxFileBytes() { return maxFileBytes; }
    public void setMaxFileBytes(long maxFileBytes) { this.maxFileBytes = maxFileBytes; }
    public int getMaxOpenFiles() { return maxOpenFiles; }
    public void setMaxOpenFiles(int maxOpenFiles) { this.maxOpenFiles = maxOpenFiles; }
    public long getOuterTimeoutGraceMs() { return outerTimeoutGraceMs; }
    public void setOuterTimeoutGraceMs(long outerTimeoutGraceMs) { this.outerTimeoutGraceMs = outerTimeoutGraceMs; }
    public long getWorkspacePollMs() { return workspacePollMs; }
    public void setWorkspacePollMs(long workspacePollMs) { this.workspacePollMs = workspacePollMs; }
}

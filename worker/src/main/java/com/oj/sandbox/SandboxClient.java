package com.oj.sandbox;

@FunctionalInterface
public interface SandboxClient {

    SandboxResult execute(SandboxRequest request);
}

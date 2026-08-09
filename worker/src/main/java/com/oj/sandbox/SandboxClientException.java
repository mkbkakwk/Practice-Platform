package com.oj.sandbox;

public class SandboxClientException extends RuntimeException {

    public SandboxClientException(String message) {
        super(message);
    }

    public SandboxClientException(String message, Throwable cause) {
        super(message, cause);
    }
}

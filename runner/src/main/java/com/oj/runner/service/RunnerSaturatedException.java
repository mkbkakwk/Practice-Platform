package com.oj.runner.service;

public class RunnerSaturatedException extends RuntimeException {

    public RunnerSaturatedException() {
        super("Runner job concurrency limit reached");
    }
}

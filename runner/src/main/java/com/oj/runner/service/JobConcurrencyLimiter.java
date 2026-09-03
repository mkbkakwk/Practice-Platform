package com.oj.runner.service;

import com.oj.runner.config.RunnerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

@Component
public class JobConcurrencyLimiter {

    private final Semaphore permits;

    @Autowired
    public JobConcurrencyLimiter(RunnerProperties properties) {
        this(properties.getMaxConcurrentJobs());
    }

    JobConcurrencyLimiter(int maxConcurrentJobs) {
        permits = new Semaphore(maxConcurrentJobs, true);
    }

    public boolean tryAcquire() {
        return permits.tryAcquire();
    }

    public void release() {
        permits.release();
    }
}

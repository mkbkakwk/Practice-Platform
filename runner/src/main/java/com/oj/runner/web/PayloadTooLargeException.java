package com.oj.runner.web;

import java.io.IOException;

public class PayloadTooLargeException extends IOException {

    public PayloadTooLargeException() {
        super("Runner request body exceeds the configured limit");
    }
}

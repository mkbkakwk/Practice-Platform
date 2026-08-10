package com.oj.runner.web;

import com.oj.runner.api.RunnerErrorResponse;
import com.oj.runner.language.RunnerRequestValidationException;
import com.oj.runner.service.RunnerSaturatedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RunnerExceptionHandler {

    @ExceptionHandler(RunnerRequestValidationException.class)
    public ResponseEntity<RunnerErrorResponse> invalidRequest(RunnerRequestValidationException exception) {
        return ResponseEntity.badRequest()
                .body(new RunnerErrorResponse("INVALID_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(RunnerSaturatedException.class)
    public ResponseEntity<RunnerErrorResponse> saturated() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new RunnerErrorResponse("RUNNER_BUSY", "Runner job concurrency limit reached"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RunnerErrorResponse> unreadable(HttpMessageNotReadableException exception) {
        if (hasCause(exception, PayloadTooLargeException.class)) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(new RunnerErrorResponse(
                            "PAYLOAD_TOO_LARGE", "Runner request body exceeds the configured limit"));
        }
        return ResponseEntity.badRequest()
                .body(new RunnerErrorResponse("INVALID_REQUEST", "Runner request JSON is invalid"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RunnerErrorResponse> unexpected() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new RunnerErrorResponse("INTERNAL_ERROR", "Runner request failed"));
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

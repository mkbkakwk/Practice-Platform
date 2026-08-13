package com.oj.contest;

import com.oj.common.ApiException;
import org.springframework.http.HttpStatus;

public class ContestException extends ApiException {
    private final String code;

    public ContestException(HttpStatus status, String code, String message) {
        super(status, message);
        this.code = code;
    }

    public String getCode() { return code; }

    public static ContestException notFound() {
        return new ContestException(HttpStatus.NOT_FOUND, "CONTEST_NOT_FOUND", "比赛不存在");
    }

    public static ContestException conflict(String code, String message) {
        return new ContestException(HttpStatus.CONFLICT, code, message);
    }

    public static ContestException forbidden(String code, String message) {
        return new ContestException(HttpStatus.FORBIDDEN, code, message);
    }
}

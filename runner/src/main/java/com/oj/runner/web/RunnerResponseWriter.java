package com.oj.runner.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.runner.api.RunnerErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RunnerResponseWriter {

    private final ObjectMapper objectMapper;

    public RunnerResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void error(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.resetBuffer();
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        objectMapper.writeValue(response.getOutputStream(), new RunnerErrorResponse(code, message));
    }
}

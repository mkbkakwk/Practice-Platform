package com.oj.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.entity.UserEntity;
import com.oj.mapper.UserMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private static final Pattern PUBLIC_PROBLEM_DETAIL =
            Pattern.compile("^/api/problems/[^/]+$");
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtInterceptor(JwtUtil jwtUtil, UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        CurrentUser.clear();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            if (isPublicRequest(request)) return true;
            writeError(response, HttpStatus.UNAUTHORIZED, "未登录");
            return false;
        }
        String token = header.substring(7);
        try {
            Claims claims = jwtUtil.verify(token);
            Integer subjectId = parseInteger(claims.getSubject());
            Integer claimedUserId = parseInteger(claims.get("userId"));
            Integer claimedVersion = parseInteger(claims.get("tokenVersion"));
            if (!Objects.equals(subjectId, claimedUserId)) {
                throw new IllegalArgumentException("JWT user mismatch");
            }

            UserEntity user = userMapper.selectById(claimedUserId);
            if (user == null || !Objects.equals(user.getTokenVersion(), claimedVersion)) {
                writeError(response, HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录");
                return false;
            }

            CurrentUser.set(user.getId(), user.getUsername(), user.getRole());
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            writeError(response, HttpStatus.UNAUTHORIZED, "登录已过期，请重新登录");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CurrentUser.clear();
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of("error", message)));
    }

    private boolean isPublicRequest(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("POST".equalsIgnoreCase(method)) {
            return "/api/auth/register".equals(path) || "/api/auth/login".equals(path);
        }
        if (!"GET".equalsIgnoreCase(method)) return false;
        if ("/api/health".equals(path)
                || "/api/submissions/meta/languages".equals(path)
                || "/api/problems".equals(path)
                || "/api/users/leaderboard".equals(path)) {
            return true;
        }
        return PUBLIC_PROBLEM_DETAIL.matcher(path).matches()
                && !"/api/problems/manage".equals(path);
    }

    private Integer parseInteger(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text && !text.isBlank()) return Integer.valueOf(text);
        throw new IllegalArgumentException("Missing integer claim");
    }
}

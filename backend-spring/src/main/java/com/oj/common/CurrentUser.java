package com.oj.common;

import io.jsonwebtoken.Claims;

import java.util.Objects;

/**
 * Holds the authenticated user for the duration of a request.
 * Set by JwtInterceptor, read by controllers/services.
 */
public class CurrentUser {
    private static final ThreadLocal<Claims> HOLDER = new ThreadLocal<>();

    public static void set(Claims claims) { HOLDER.set(claims); }
    public static Claims get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }

    public static Integer getId() {
        Claims c = HOLDER.get();
        return c == null ? null : Integer.valueOf(c.getSubject());
    }

    public static String getUsername() {
        Claims c = HOLDER.get();
        return c == null ? null : c.get("username", String.class);
    }

    public static String getRole() {
        Claims c = HOLDER.get();
        return c == null ? null : c.get("role", String.class);
    }

    public static boolean isAdmin() {
        return "ADMIN".equals(getRole());
    }

    public static boolean isTeacher() {
        return "TEACHER".equals(getRole());
    }

    public static boolean isTeacherOrAdmin() {
        String role = getRole();
        return "ADMIN".equals(role) || "TEACHER".equals(role);
    }

    /** Admin manages any content; a teacher manages only non-system content they created. */
    public static boolean canManage(Integer createdBy) {
        if (isAdmin()) return true;
        return isTeacher() && createdBy != null && Objects.equals(createdBy, getId());
    }

    public static void requireContentManager() {
        if (!isTeacherOrAdmin()) {
            throw ApiException.forbidden("需要教师或管理员权限");
        }
    }

    public static void requireCanManage(Integer createdBy) {
        requireContentManager();
        if (!canManage(createdBy)) {
            throw ApiException.forbidden("无权管理该内容");
        }
    }
}

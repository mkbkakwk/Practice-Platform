package com.oj.common;

import java.util.Objects;

/**
 * Holds the authenticated user for the duration of a request.
 * Set by JwtInterceptor from the current database row, then read by
 * controllers/services. JWT claims are never the permission source.
 */
public class CurrentUser {
    private static final ThreadLocal<AuthenticatedUser> HOLDER = new ThreadLocal<>();

    public static void set(Integer id, String username, String role) {
        HOLDER.set(new AuthenticatedUser(id, username, role));
    }
    public static AuthenticatedUser get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }

    public static Integer getId() {
        AuthenticatedUser user = HOLDER.get();
        return user == null ? null : user.id();
    }

    public static String getUsername() {
        AuthenticatedUser user = HOLDER.get();
        return user == null ? null : user.username();
    }

    public static String getRole() {
        AuthenticatedUser user = HOLDER.get();
        return user == null ? null : user.role();
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

    public record AuthenticatedUser(Integer id, String username, String role) {}
}

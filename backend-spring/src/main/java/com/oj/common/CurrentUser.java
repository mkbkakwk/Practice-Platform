package com.oj.common;

import io.jsonwebtoken.Claims;

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

    /** Teachers and admins can both manage exercises and review submissions. */
    public static boolean isTeacherOrAdmin() {
        String r = getRole();
        return "ADMIN".equals(r) || "TEACHER".equals(r);
    }
}

package com.oj.contest;

public enum ContentVisibility {
    PUBLIC,
    CONTEST_ONLY;

    public static ContentVisibility parse(String value) {
        if (value == null || value.isBlank()) return PUBLIC;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw com.oj.common.ApiException.badRequest("内容可见范围无效");
        }
    }
}

package com.oj.dto;

import java.time.LocalDateTime;

public class SubmissionView {
    private Integer id;
    private Integer userId;
    private Integer problemId;
    private String language;
    private String code;
    private String verdict;
    private Integer timeMs;
    private Integer memoryKb;
    private String message;
    private Integer passed;
    private Integer total;
    private LocalDateTime createdAt;
    // optional joins
    private ProblemBrief problem;
    private UserBrief user;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public Integer getProblemId() { return problemId; }
    public void setProblemId(Integer problemId) { this.problemId = problemId; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }
    public Integer getTimeMs() { return timeMs; }
    public void setTimeMs(Integer timeMs) { this.timeMs = timeMs; }
    public Integer getMemoryKb() { return memoryKb; }
    public void setMemoryKb(Integer memoryKb) { this.memoryKb = memoryKb; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Integer getPassed() { return passed; }
    public void setPassed(Integer passed) { this.passed = passed; }
    public Integer getTotal() { return total; }
    public void setTotal(Integer total) { this.total = total; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public ProblemBrief getProblem() { return problem; }
    public void setProblem(ProblemBrief problem) { this.problem = problem; }
    public UserBrief getUser() { return user; }
    public void setUser(UserBrief user) { this.user = user; }

    public static class ProblemBrief {
        private Integer id;
        private String slug;
        private String title;
        private String difficulty;
        public ProblemBrief() {}
        public ProblemBrief(Integer id, String slug, String title, String difficulty) {
            this.id = id; this.slug = slug; this.title = title; this.difficulty = difficulty;
        }
        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }
        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDifficulty() { return difficulty; }
        public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    }

    public static class UserBrief {
        private Integer id;
        private String username;
        public UserBrief() {}
        public UserBrief(Integer id, String username) { this.id = id; this.username = username; }
        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
    }
}

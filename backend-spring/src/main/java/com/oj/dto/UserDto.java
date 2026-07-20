package com.oj.dto;

public class UserDto {
    private Integer id;
    private String username;
    private String role;
    private Integer solvedCount;

    public UserDto() {}
    public UserDto(Integer id, String username, String role, Integer solvedCount) {
        this.id = id; this.username = username; this.role = role; this.solvedCount = solvedCount;
    }
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Integer getSolvedCount() { return solvedCount; }
    public void setSolvedCount(Integer solvedCount) { this.solvedCount = solvedCount; }
}

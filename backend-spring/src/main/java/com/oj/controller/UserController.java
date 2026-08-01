package com.oj.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import com.oj.dto.SubmissionView;
import com.oj.entity.UserEntity;
import com.oj.mapper.UserMapper;
import com.oj.service.SubmissionService;
import com.oj.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserMapper userMapper;
    private final SubmissionService submissionService;
    private final UserService userService;

    public UserController(UserMapper userMapper, SubmissionService submissionService,
                          UserService userService) {
        this.userMapper = userMapper;
        this.submissionService = submissionService;
        this.userService = userService;
    }

    @GetMapping("/leaderboard")
    public Map<String, Object> leaderboard(@RequestParam(defaultValue = "20") int limit) {
        limit = Math.min(100, Math.max(1, limit));
        QueryWrapper<UserEntity> qw = new QueryWrapper<>();
        qw.orderByDesc("solved_count").orderByAsc("created_at").last("LIMIT " + limit);
        List<UserEntity> users = userMapper.selectList(qw);
        AtomicInteger rank = new AtomicInteger(1);
        List<Map<String, Object>> ranked = users.stream().map(u -> Map.<String, Object>of(
                "rank", rank.getAndIncrement(),
                "id", u.getId(),
                "username", u.getUsername(),
                "role", u.getRole(),
                "solvedCount", u.getSolvedCount(),
                "createdAt", u.getCreatedAt()
        )).toList();
        return Map.of("leaderboard", ranked);
    }

    @GetMapping("/me/submissions")
    public Map<String, Object> mySubmissions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Integer userId = CurrentUser.getId();
        if (userId == null) throw ApiException.unauthorized("请先登录");
        page = Math.max(1, page);
        pageSize = Math.min(50, Math.max(1, pageSize));
        List<SubmissionView> subs = submissionService.mySubmissions(userId, page, pageSize);
        long total = submissionService.countMine(userId);
        return Map.of("total", total, "page", page, "pageSize", pageSize, "submissions", subs);
    }

    @GetMapping
    public Map<String, Object> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        assertAdmin();
        page = Math.max(1, page);
        pageSize = Math.min(100, Math.max(1, pageSize));
        Page<UserEntity> p = userMapper.selectPage(new Page<>(page, pageSize),
                new QueryWrapper<UserEntity>().orderByDesc("id"));
        List<Map<String, Object>> items = p.getRecords().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("role", u.getRole());
            m.put("solvedCount", u.getSolvedCount());
            m.put("createdAt", u.getCreatedAt());
            return m;
        }).toList();
        return Map.of("total", p.getTotal(), "page", page, "pageSize", pageSize, "users", items);
    }

    @PutMapping("/{id}/role")
    public Map<String, Object> updateRole(@PathVariable int id, @RequestBody Map<String, String> body) {
        UserEntity user = userService.updateRole(id, body.get("role"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("role", user.getRole());
        result.put("solvedCount", user.getSolvedCount());
        result.put("createdAt", user.getCreatedAt());
        return Map.of("user", result);
    }

    @DeleteMapping("/{id}")
    public Map<String, String> deleteUser(@PathVariable int id) {
        userService.deleteUser(id);
        return Map.of("message", "用户已删除");
    }

    private void assertAdmin() {
        if (!CurrentUser.isAdmin()) {
            throw ApiException.forbidden("需要管理员权限");
        }
    }
}

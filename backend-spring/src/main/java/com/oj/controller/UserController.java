package com.oj.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import com.oj.dto.SubmissionView;
import com.oj.entity.UserEntity;
import com.oj.mapper.UserMapper;
import com.oj.service.SubmissionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserMapper userMapper;
    private final SubmissionService submissionService;

    public UserController(UserMapper userMapper, SubmissionService submissionService) {
        this.userMapper = userMapper;
        this.submissionService = submissionService;
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
}

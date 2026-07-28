package com.oj.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.oj.common.ApiException;
import com.oj.common.JwtUtil;
import com.oj.config.AppProperties;
import com.oj.dto.AuthResponse;
import com.oj.dto.LoginRequest;
import com.oj.dto.RegisterRequest;
import com.oj.dto.UserDto;
import com.oj.entity.UserEntity;
import com.oj.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final AppProperties props;

    public AuthService(UserMapper userMapper, JwtUtil jwtUtil, AppProperties props) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.props = props;
    }

    public AuthResponse register(RegisterRequest req) {
        UserEntity exists = userMapper.selectOne(new QueryWrapper<UserEntity>().eq("username", req.getUsername()));
        if (exists != null) {
            throw ApiException.conflict("用户名已被占用");
        }

        // Public registration is always USER. Teachers are set by admin in
        // the user management page. Only the first registered user is auto-
        // promoted to ADMIN (controlled by PROMOTE_FIRST_ADMIN config).
        String role = "USER";
        if (props.isPromoteFirstAdmin()) {
            Long count = userMapper.selectCount(null);
            if (count == 0) {
                role = "ADMIN";
            }
        }

        String hashed = BCrypt.withDefaults().hashToString(12, req.getPassword().toCharArray());
        UserEntity user = new UserEntity();
        user.setUsername(req.getUsername());
        user.setPassword(hashed);
        user.setRole(role);
        user.setSolvedCount(0);
        userMapper.insert(user);

        return buildResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        if (req.getUsername() == null || req.getUsername().isBlank()
                || req.getPassword() == null || req.getPassword().isBlank()) {
            throw ApiException.badRequest("请输入用户名和密码");
        }
        UserEntity user = userMapper.selectOne(new QueryWrapper<UserEntity>().eq("username", req.getUsername()));
        if (user == null) {
            throw ApiException.unauthorized("用户名或密码错误");
        }
        boolean ok = BCrypt.verifyer().verify(req.getPassword().toCharArray(), user.getPassword()).verified;
        if (!ok) {
            throw ApiException.unauthorized("用户名或密码错误");
        }
        return buildResponse(user);
    }

    public UserDto getCurrentUser(int id) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw ApiException.notFound("用户不存在");
        }
        return new UserDto(user.getId(), user.getUsername(), user.getRole(), user.getSolvedCount());
    }

    private AuthResponse buildResponse(UserEntity user) {
        String token = jwtUtil.sign(user.getId(), user.getUsername(), user.getRole());
        UserDto dto = new UserDto(user.getId(), user.getUsername(), user.getRole(), user.getSolvedCount());
        return new AuthResponse(token, dto);
    }
}

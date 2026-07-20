package com.oj.controller;

import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import com.oj.dto.AuthResponse;
import com.oj.dto.LoginRequest;
import com.oj.dto.RegisterRequest;
import com.oj.dto.UserDto;
import com.oj.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        Integer id = CurrentUser.getId();
        if (id == null) throw ApiException.unauthorized("未登录");
        UserDto user = authService.getCurrentUser(id);
        return Map.of("user", user);
    }
}

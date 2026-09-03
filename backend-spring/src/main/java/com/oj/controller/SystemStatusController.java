package com.oj.controller;

import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import com.oj.observability.SystemStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class SystemStatusController {
    private final SystemStatusService systemStatus;

    public SystemStatusController(SystemStatusService systemStatus) { this.systemStatus = systemStatus; }

    @GetMapping("/system-status")
    public Map<String, Object> status() {
        if (!CurrentUser.isAdmin()) throw ApiException.forbidden("需要管理员权限");
        return systemStatus.snapshot();
    }
}

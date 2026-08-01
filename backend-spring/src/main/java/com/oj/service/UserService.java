package com.oj.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.oj.common.AdministratorLock;
import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import com.oj.entity.UserEntity;
import com.oj.mapper.UserMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
public class UserService {

    private static final Set<String> VALID_ROLES = Set.of("USER", "TEACHER", "ADMIN");

    private final UserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AdministratorLock administratorLock;

    public UserService(UserMapper userMapper, JdbcTemplate jdbcTemplate,
                       AdministratorLock administratorLock) {
        this.userMapper = userMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.administratorLock = administratorLock;
    }

    @Transactional
    public UserEntity updateRole(int id, String requestedRole) {
        assertAdmin();
        if (requestedRole == null) {
            throw ApiException.badRequest("角色必须是 USER / TEACHER / ADMIN");
        }
        String newRole = requestedRole.toUpperCase(Locale.ROOT);
        if (!VALID_ROLES.contains(newRole)) {
            throw ApiException.badRequest("角色必须是 USER / TEACHER / ADMIN");
        }

        administratorLock.acquire();
        UserEntity user = userMapper.selectByIdForUpdate(id);
        if (user == null) {
            throw ApiException.notFound("用户不存在");
        }
        if (user.getRole().equals(newRole)) {
            return user;
        }
        if ("ADMIN".equals(user.getRole()) && !"ADMIN".equals(newRole)) {
            assertAnotherAdministratorExists();
        }

        userMapper.updateRoleAndInvalidateTokens(id, newRole);
        user.setRole(newRole);
        user.setTokenVersion(user.getTokenVersion() + 1);
        return user;
    }

    @Transactional
    public void deleteUser(int id) {
        assertAdmin();
        administratorLock.acquire();
        UserEntity user = userMapper.selectByIdForUpdate(id);
        if (user == null) {
            throw ApiException.notFound("用户不存在");
        }
        if ("ADMIN".equals(user.getRole())) {
            assertAnotherAdministratorExists();
        }
        if (hasHistoricalActivity(id)) {
            throw ApiException.conflict("该用户已有提交或作答记录，不能删除");
        }
        userMapper.deleteById(id);
    }

    private boolean hasHistoricalActivity(int userId) {
        Boolean result = jdbcTemplate.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM "Submission" WHERE user_id = ?)
                    OR EXISTS (SELECT 1 FROM "OfficeRecord" WHERE user_id = ?)
                    OR EXISTS (SELECT 1 FROM "OfficeDocSubmission" WHERE user_id = ?)
                """, Boolean.class, userId, userId, userId);
        return Boolean.TRUE.equals(result);
    }

    private void assertAnotherAdministratorExists() {
        Long count = userMapper.selectCount(
                new QueryWrapper<UserEntity>().eq("role", "ADMIN"));
        if (count == null || count <= 1) {
            throw ApiException.conflict("不能降级或删除最后一个管理员");
        }
    }

    private void assertAdmin() {
        if (!CurrentUser.isAdmin()) {
            throw ApiException.forbidden("需要管理员权限");
        }
    }
}

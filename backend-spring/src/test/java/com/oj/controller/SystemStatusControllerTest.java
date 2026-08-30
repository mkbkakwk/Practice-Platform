package com.oj.controller;

import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import com.oj.observability.SystemStatusService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemStatusControllerTest {
    @AfterEach
    void clearUser() { CurrentUser.clear(); }

    @Test
    void onlyAdminCanReadTheSnapshot() {
        SystemStatusService status = mock(SystemStatusService.class);
        SystemStatusController controller = new SystemStatusController(status);
        assertThrows(ApiException.class, controller::status);

        CurrentUser.set(1, "teacher", "TEACHER");
        assertThrows(ApiException.class, controller::status);

        CurrentUser.set(3, "student", "STUDENT");
        assertThrows(ApiException.class, controller::status);

        CurrentUser.set(2, "admin", "ADMIN");
        when(status.snapshot()).thenReturn(Map.of("checkedAt", "2026-08-29T00:00:00Z"));
        assertEquals("2026-08-29T00:00:00Z", controller.status().get("checkedAt"));
    }
}

package com.mes.system.adapter.web;

import com.mes.system.application.SystemApplicationService;
import com.mes.system.domain.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemManagementController.class)
@Import({SecurityConfiguration.class, CorrelationIdFilter.class, GlobalExceptionHandler.class})
class SystemManagementSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private SystemApplicationService service;

    @Test
    @WithMockUser(authorities = "ROLE_READ")
    void userListRequiresExactPermissionCode() throws Exception {
        mvc.perform(get("/api/system-management/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "USER_READ")
    void userListAllowsMatchingPermission() throws Exception {
        when(service.users(1, 20)).thenReturn(new PageResult<>(0, List.of()));

        mvc.perform(get("/api/system-management/users"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "USER_WRITE")
    void writeStillRequiresCsrf() throws Exception {
        mvc.perform(post("/api/system-management/users")
                        .contentType("application/json")
                        .content("""
                                {"username":"operator","displayName":"操作员",
                                 "password":"StrongPassword!123","enabled":true}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_WRITE")
    void authorizedWriteWithCsrfReachesValidationLayer() throws Exception {
        mvc.perform(post("/api/system-management/roles")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"\",\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void probeEndpointRejectsUnrelatedAdministratorPermission() throws Exception {
        mvc.perform(get("/api/modbus-probe/sessions/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "MODBUS_PROBE")
    void probeEndpointAllowsDedicatedPermissionThroughSecurityLayer() throws Exception {
        // 测试切片中未加载探针控制器，404 表示请求已通过安全层。
        mvc.perform(get("/api/modbus-probe/sessions/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }
}

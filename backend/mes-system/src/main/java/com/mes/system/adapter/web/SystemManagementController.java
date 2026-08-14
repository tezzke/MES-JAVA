package com.mes.system.adapter.web;

import com.mes.system.application.SystemApplicationService;
import com.mes.system.domain.AuditEvent;
import com.mes.system.domain.Menu;
import com.mes.system.domain.PageResult;
import com.mes.system.domain.Role;
import com.mes.system.domain.SystemUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户、角色、菜单和审计 API。控制器只负责协议转换，不包含持久化细节。
 */
@RestController
@RequestMapping("/api/system-management")
public class SystemManagementController {

    private final SystemApplicationService service;

    public SystemManagementController(SystemApplicationService service) {
        this.service = service;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('USER_READ')")
    public PageResult<SystemUser.UserView> users(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.users(page, size);
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createUser(@Valid @RequestBody UserRequest body, Principal principal,
                                        HttpServletRequest request) {
        long id = service.saveUser(null, body.username(), body.displayName(), body.password(),
                body.enabled(), principal.getName(), correlationId(request));
        return Map.of("id", id);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public Map<String, Long> updateUser(@PathVariable long id, @Valid @RequestBody UserRequest body,
                                        Principal principal, HttpServletRequest request) {
        service.saveUser(id, body.username(), body.displayName(), body.password(),
                body.enabled(), principal.getName(), correlationId(request));
        return Map.of("id", id);
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable long id, Principal principal, HttpServletRequest request) {
        service.deleteUser(id, principal.getName(), correlationId(request));
    }

    @PutMapping("/users/{id}/roles")
    @PreAuthorize("hasAuthority('USER_AUTHORIZE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void authorizeUser(@PathVariable long id, @RequestBody Set<Long> roleIds,
                              Principal principal, HttpServletRequest request) {
        service.assignUserRoles(id, roleIds, principal.getName(), correlationId(request));
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    public List<Role> roles() {
        return service.roles();
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    public List<String> permissions() {
        return service.permissions();
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createRole(@Valid @RequestBody RoleRequest body, Principal principal,
                                        HttpServletRequest request) {
        return Map.of("id", service.saveRole(null, body.code(), body.name(), principal.getName(),
                correlationId(request)));
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ROLE_WRITE')")
    public Map<String, Long> updateRole(@PathVariable long id, @Valid @RequestBody RoleRequest body,
                                        Principal principal, HttpServletRequest request) {
        service.saveRole(id, body.code(), body.name(), principal.getName(), correlationId(request));
        return Map.of("id", id);
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ROLE_WRITE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRole(@PathVariable long id, Principal principal, HttpServletRequest request) {
        service.deleteRole(id, principal.getName(), correlationId(request));
    }

    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('ROLE_AUTHORIZE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void authorizeRole(@PathVariable long id, @RequestBody Set<String> permissions,
                              Principal principal, HttpServletRequest request) {
        service.authorizeRole(id, permissions, principal.getName(), correlationId(request));
    }

    @GetMapping("/menus")
    @PreAuthorize("hasAuthority('MENU_READ')")
    public List<Menu> menus() {
        return service.menus();
    }

    @PostMapping("/menus")
    @PreAuthorize("hasAuthority('MENU_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createMenu(@Valid @RequestBody MenuRequest body, Principal principal,
                                        HttpServletRequest request) {
        return Map.of("id", service.saveMenu(body.toMenu(null), principal.getName(),
                correlationId(request)));
    }

    @PutMapping("/menus/{id}")
    @PreAuthorize("hasAuthority('MENU_WRITE')")
    public Map<String, Long> updateMenu(@PathVariable long id, @Valid @RequestBody MenuRequest body,
                                        Principal principal, HttpServletRequest request) {
        service.saveMenu(body.toMenu(id), principal.getName(), correlationId(request));
        return Map.of("id", id);
    }

    @DeleteMapping("/menus/{id}")
    @PreAuthorize("hasAuthority('MENU_WRITE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMenu(@PathVariable long id, Principal principal, HttpServletRequest request) {
        service.deleteMenu(id, principal.getName(), correlationId(request));
    }

    @GetMapping("/audits")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public PageResult<AuditEvent> audits(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.audits(page, size);
    }

    private static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        return value == null ? "" : value.toString();
    }

    /** 用户保存请求，更新时密码可留空以保留原摘要。 */
    public record UserRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 100) String displayName,
            @Size(max = 128) String password,
            boolean enabled) {
    }

    /** 角色保存请求。 */
    public record RoleRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 100) String name) {
    }

    /** 菜单保存请求。 */
    public record MenuRequest(Long parentId, @NotBlank @Size(max = 100) String name,
                              @NotBlank @Size(max = 255) String path,
                              @Size(max = 100) String permissionCode,
                              int sortOrder, boolean enabled) {
        Menu toMenu(Long id) {
            return new Menu(id, parentId, name, path, permissionCode, sortOrder, enabled);
        }
    }
}

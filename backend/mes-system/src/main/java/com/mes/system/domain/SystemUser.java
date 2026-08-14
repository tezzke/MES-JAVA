package com.mes.system.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.Set;

/**
 * 系统用户。密码摘要仅供认证流程使用，不通过接口序列化。
 */
public record SystemUser(
        Long id,
        String username,
        String displayName,
        @JsonIgnore String passwordHash,
        boolean enabled,
        int failedAttempts,
        Instant lockedUntil,
        Set<String> roles,
        Set<String> permissions) {

    public SystemUser {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    /** 返回不包含密码摘要的安全视图。 */
    public UserView toView() {
        return new UserView(id, username, displayName, enabled, roles, permissions);
    }

    /** 对外用户视图。 */
    public record UserView(Long id, String username, String displayName, boolean enabled,
                           Set<String> roles, Set<String> permissions) {
    }
}

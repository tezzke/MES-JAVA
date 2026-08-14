package com.mes.system.domain;

import java.util.Set;

/** 系统角色及其权限码。 */
public record Role(Long id, String code, String name, Set<String> permissions) {
    public Role {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}

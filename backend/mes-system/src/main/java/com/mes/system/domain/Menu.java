package com.mes.system.domain;

/** 前端导航菜单，permissionCode 为空时仅要求登录。 */
public record Menu(Long id, Long parentId, String name, String path,
                   String permissionCode, int sortOrder, boolean enabled) {
}

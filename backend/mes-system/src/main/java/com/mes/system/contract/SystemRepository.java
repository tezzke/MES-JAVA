package com.mes.system.contract;

import com.mes.system.domain.AuditEvent;
import com.mes.system.domain.Menu;
import com.mes.system.domain.PageResult;
import com.mes.system.domain.Role;
import com.mes.system.domain.SystemUser;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 系统域持久化端口。应用层只依赖本接口，不感知 JDBC 或 MySQL。
 */
public interface SystemRepository {
    Optional<SystemUser> findUserByUsername(String username);
    Optional<SystemUser> findUser(long id);
    PageResult<SystemUser.UserView> findUsers(int page, int size);
    long saveUser(Long id, String username, String displayName, String passwordHash, boolean enabled);
    void deleteUser(long id);
    void setUserRoles(long userId, Set<Long> roleIds);
    void recordLoginFailure(String username, int attempts, Instant lockedUntil);
    void clearLoginFailures(long userId);

    List<Role> findRoles();
    List<String> findPermissionCodes();
    long saveRole(Long id, String code, String name);
    void deleteRole(long id);
    void setRolePermissions(long roleId, Set<String> permissionCodes);

    List<Menu> findMenus();
    long saveMenu(Menu menu);
    void deleteMenu(long id);

    void appendAudit(AuditEvent event);
    PageResult<AuditEvent> findAudits(int page, int size);
}

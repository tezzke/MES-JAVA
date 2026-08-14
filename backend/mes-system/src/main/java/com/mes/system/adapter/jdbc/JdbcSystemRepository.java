package com.mes.system.adapter.jdbc;

import com.mes.system.contract.SystemRepository;
import com.mes.system.domain.AuditEvent;
import com.mes.system.domain.Menu;
import com.mes.system.domain.PageResult;
import com.mes.system.domain.Role;
import com.mes.system.domain.SystemUser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 系统域 MySQL JDBC 适配器。所有 SQL 和业务库依赖均封装在本类。
 */
@Repository
public class JdbcSystemRepository implements SystemRepository {

    private final JdbcTemplate jdbc;

    public JdbcSystemRepository(@Qualifier("businessJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SystemUser> findUserByUsername(String username) {
        List<SystemUser> users = jdbc.query("""
                SELECT id, username, display_name, password_hash, enabled, failed_attempts, locked_until
                FROM sys_user WHERE username = ?""", (rs, row) -> mapUser(
                rs.getLong("id"), rs.getString("username"), rs.getString("display_name"),
                rs.getString("password_hash"), rs.getBoolean("enabled"), rs.getInt("failed_attempts"),
                toInstant(rs.getTimestamp("locked_until"))), username);
        return users.stream().findFirst();
    }

    @Override
    public Optional<SystemUser> findUser(long id) {
        List<SystemUser> users = jdbc.query("""
                SELECT id, username, display_name, password_hash, enabled, failed_attempts, locked_until
                FROM sys_user WHERE id = ?""", (rs, row) -> mapUser(
                rs.getLong("id"), rs.getString("username"), rs.getString("display_name"),
                rs.getString("password_hash"), rs.getBoolean("enabled"), rs.getInt("failed_attempts"),
                toInstant(rs.getTimestamp("locked_until"))), id);
        return users.stream().findFirst();
    }

    @Override
    public PageResult<SystemUser.UserView> findUsers(int page, int size) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Long.class);
        List<SystemUser.UserView> items = jdbc.query("""
                SELECT id, username, display_name, enabled FROM sys_user
                ORDER BY id LIMIT ? OFFSET ?""", (rs, row) -> new SystemUser.UserView(
                rs.getLong("id"), rs.getString("username"), rs.getString("display_name"),
                rs.getBoolean("enabled"), roles(rs.getLong("id")), permissions(rs.getLong("id"))),
                size, (long) (page - 1) * size);
        return new PageResult<>(total == null ? 0 : total, items);
    }

    @Override
    public long saveUser(Long id, String username, String displayName, String passwordHash, boolean enabled) {
        if (id != null) {
            if (passwordHash == null) {
                jdbc.update("UPDATE sys_user SET username=?, display_name=?, enabled=? WHERE id=?",
                        username, displayName, enabled, id);
            } else {
                jdbc.update("""
                        UPDATE sys_user SET username=?, display_name=?, password_hash=?, enabled=?
                        WHERE id=?""", username, displayName, passwordHash, enabled, id);
            }
            return id;
        }
        if (passwordHash == null) {
            throw new IllegalArgumentException("新用户必须设置密码");
        }
        return insertAndReturnKey("""
                INSERT INTO sys_user(username, display_name, password_hash, enabled)
                VALUES (?, ?, ?, ?)""", username, displayName, passwordHash, enabled);
    }

    @Override
    public void deleteUser(long id) {
        jdbc.update("DELETE FROM sys_user WHERE id=?", id);
    }

    @Override
    public void setUserRoles(long userId, Set<Long> roleIds) {
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", userId);
        for (Long roleId : roleIds) {
            jdbc.update("INSERT INTO sys_user_role(user_id, role_id) VALUES (?, ?)", userId, roleId);
        }
    }

    @Override
    public void recordLoginFailure(String username, int attempts, Instant lockedUntil) {
        jdbc.update("UPDATE sys_user SET failed_attempts=?, locked_until=? WHERE username=?",
                attempts, timestamp(lockedUntil), username);
    }

    @Override
    public void clearLoginFailures(long userId) {
        jdbc.update("UPDATE sys_user SET failed_attempts=0, locked_until=NULL WHERE id=?", userId);
    }

    @Override
    public List<Role> findRoles() {
        return jdbc.query("SELECT id, code, name FROM sys_role ORDER BY id",
                (rs, row) -> new Role(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                        rolePermissions(rs.getLong("id"))));
    }

    @Override
    public List<String> findPermissionCodes() {
        return jdbc.queryForList("SELECT code FROM sys_permission ORDER BY code", String.class);
    }

    @Override
    public long saveRole(Long id, String code, String name) {
        if (id != null) {
            jdbc.update("UPDATE sys_role SET code=?, name=? WHERE id=?", code, name, id);
            return id;
        }
        return insertAndReturnKey("INSERT INTO sys_role(code, name) VALUES (?, ?)", code, name);
    }

    @Override
    public void deleteRole(long id) {
        jdbc.update("DELETE FROM sys_role WHERE id=?", id);
    }

    @Override
    public void setRolePermissions(long roleId, Set<String> permissionCodes) {
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=?", roleId);
        for (String code : permissionCodes) {
            jdbc.update("""
                    INSERT INTO sys_role_permission(role_id, permission_id)
                    SELECT ?, id FROM sys_permission WHERE code=?""", roleId, code);
        }
    }

    @Override
    public List<Menu> findMenus() {
        return jdbc.query("""
                SELECT id, parent_id, name, path, permission_code, sort_order, enabled
                FROM sys_menu ORDER BY sort_order, id""", (rs, row) -> {
            long parentId = rs.getLong("parent_id");
            return new Menu(rs.getLong("id"), rs.wasNull() ? null : parentId,
                    rs.getString("name"), rs.getString("path"), rs.getString("permission_code"),
                    rs.getInt("sort_order"), rs.getBoolean("enabled"));
        });
    }

    @Override
    public long saveMenu(Menu menu) {
        if (menu.id() != null) {
            jdbc.update("""
                    UPDATE sys_menu SET parent_id=?, name=?, path=?, permission_code=?,
                    sort_order=?, enabled=? WHERE id=?""", menu.parentId(), menu.name(), menu.path(),
                    menu.permissionCode(), menu.sortOrder(), menu.enabled(), menu.id());
            return menu.id();
        }
        return insertAndReturnKey("""
                INSERT INTO sys_menu(parent_id, name, path, permission_code, sort_order, enabled)
                VALUES (?, ?, ?, ?, ?, ?)""", menu.parentId(), menu.name(), menu.path(),
                menu.permissionCode(), menu.sortOrder(), menu.enabled());
    }

    @Override
    public void deleteMenu(long id) {
        jdbc.update("DELETE FROM sys_menu WHERE id=?", id);
    }

    @Override
    public void appendAudit(AuditEvent event) {
        jdbc.update("""
                INSERT INTO sys_audit(actor, action, target_type, target_id, result,
                                      correlation_id, client_ip, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", event.actor(), event.action(), event.targetType(),
                event.targetId(), event.result(), event.correlationId(), event.clientIp(),
                timestamp(event.occurredAt()));
    }

    @Override
    public PageResult<AuditEvent> findAudits(int page, int size) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit", Long.class);
        List<AuditEvent> items = jdbc.query("""
                SELECT id, actor, action, target_type, target_id, result,
                       correlation_id, client_ip, occurred_at
                FROM sys_audit ORDER BY occurred_at DESC LIMIT ? OFFSET ?""", (rs, row) ->
                new AuditEvent(rs.getLong("id"), rs.getString("actor"), rs.getString("action"),
                        rs.getString("target_type"), rs.getString("target_id"), rs.getString("result"),
                        rs.getString("correlation_id"), rs.getString("client_ip"),
                        toInstant(rs.getTimestamp("occurred_at"))),
                size, (long) (page - 1) * size);
        return new PageResult<>(total == null ? 0 : total, items);
    }

    private SystemUser mapUser(long id, String username, String displayName, String passwordHash,
                               boolean enabled, int failedAttempts, Instant lockedUntil) {
        return new SystemUser(id, username, displayName, passwordHash, enabled, failedAttempts,
                lockedUntil, roles(id), permissions(id));
    }

    private Set<String> roles(long userId) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT r.code FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.id
                WHERE ur.user_id=?""", String.class, userId));
    }

    private Set<String> permissions(long userId) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT DISTINCT p.code FROM sys_permission p
                JOIN sys_role_permission rp ON rp.permission_id=p.id
                JOIN sys_user_role ur ON ur.role_id=rp.role_id WHERE ur.user_id=?""",
                String.class, userId));
    }

    private Set<String> rolePermissions(long roleId) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT p.code FROM sys_permission p
                JOIN sys_role_permission rp ON rp.permission_id=p.id WHERE rp.role_id=?""",
                String.class, roleId));
    }

    private long insertAndReturnKey(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("数据库未返回主键");
        }
        return key.longValue();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}

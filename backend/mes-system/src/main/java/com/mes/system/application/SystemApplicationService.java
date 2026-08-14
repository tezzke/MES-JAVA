package com.mes.system.application;

import com.mes.system.contract.SystemRepository;
import com.mes.system.domain.AuditEvent;
import com.mes.system.domain.Menu;
import com.mes.system.domain.PageResult;
import com.mes.system.domain.Role;
import com.mes.system.domain.SystemUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 系统管理用例编排，负责密码、登录锁定和关键写操作审计。
 */
@Service
public class SystemApplicationService implements UserDetailsService {

    private static final int MAX_LOGIN_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final SystemRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Autowired
    public SystemApplicationService(SystemRepository repository, PasswordEncoder passwordEncoder) {
        this(repository, passwordEncoder, Clock.systemUTC());
    }

    SystemApplicationService(SystemRepository repository, PasswordEncoder passwordEncoder, Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        SystemUser user = repository.findUserByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户名或密码错误"));
        Instant now = clock.instant();
        boolean unlocked = user.lockedUntil() == null || !user.lockedUntil().isAfter(now);
        List<SimpleGrantedAuthority> authorities = user.permissions().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return org.springframework.security.core.userdetails.User.withUsername(user.username())
                .password(user.passwordHash())
                .disabled(!user.enabled())
                .accountLocked(!unlocked)
                .authorities(authorities)
                .build();
    }

    /** 记录失败次数，第五次起锁定十五分钟。 */
    @Transactional("businessTransactionManager")
    public void loginFailed(String username) {
        repository.findUserByUsername(username).ifPresent(user -> {
            int attempts = user.failedAttempts() + 1;
            Instant lockedUntil = attempts >= MAX_LOGIN_FAILURES
                    ? clock.instant().plus(LOCK_DURATION) : null;
            repository.recordLoginFailure(username, attempts, lockedUntil);
            audit(username, "AUTH_LOGIN", "USER", String.valueOf(user.id()), "FAILED", null, null);
        });
    }

    @Transactional("businessTransactionManager")
    public void loginSucceeded(String username, String correlationId, String clientIp) {
        repository.findUserByUsername(username).ifPresent(user -> {
            repository.clearLoginFailures(user.id());
            audit(username, "AUTH_LOGIN", "USER", String.valueOf(user.id()), "SUCCESS",
                    correlationId, clientIp);
        });
    }

    public SystemUser.UserView currentUser(String username) {
        return repository.findUserByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在")).toView();
    }

    public PageResult<SystemUser.UserView> users(int page, int size) {
        return repository.findUsers(page, size);
    }

    @Transactional("businessTransactionManager")
    public long saveUser(Long id, String username, String displayName, String password,
                         boolean enabled, String actor, String correlationId) {
        String hash = password == null || password.isBlank() ? null : passwordEncoder.encode(password);
        long savedId = repository.saveUser(id, username, displayName, hash, enabled);
        audit(actor, id == null ? "USER_CREATE" : "USER_UPDATE", "USER",
                String.valueOf(savedId), "SUCCESS", correlationId, null);
        return savedId;
    }

    @Transactional("businessTransactionManager")
    public void deleteUser(long id, String actor, String correlationId) {
        repository.deleteUser(id);
        audit(actor, "USER_DELETE", "USER", String.valueOf(id), "SUCCESS", correlationId, null);
    }

    @Transactional("businessTransactionManager")
    public void assignUserRoles(long id, Set<Long> roleIds, String actor, String correlationId) {
        repository.setUserRoles(id, roleIds);
        audit(actor, "USER_AUTHORIZE", "USER", String.valueOf(id), "SUCCESS", correlationId, null);
    }

    public List<Role> roles() {
        return repository.findRoles();
    }

    public List<String> permissions() {
        return repository.findPermissionCodes();
    }

    @Transactional("businessTransactionManager")
    public long saveRole(Long id, String code, String name, String actor, String correlationId) {
        long savedId = repository.saveRole(id, code, name);
        audit(actor, id == null ? "ROLE_CREATE" : "ROLE_UPDATE", "ROLE",
                String.valueOf(savedId), "SUCCESS", correlationId, null);
        return savedId;
    }

    @Transactional("businessTransactionManager")
    public void deleteRole(long id, String actor, String correlationId) {
        repository.deleteRole(id);
        audit(actor, "ROLE_DELETE", "ROLE", String.valueOf(id), "SUCCESS", correlationId, null);
    }

    @Transactional("businessTransactionManager")
    public void authorizeRole(long id, Set<String> permissions, String actor, String correlationId) {
        repository.setRolePermissions(id, permissions);
        audit(actor, "ROLE_AUTHORIZE", "ROLE", String.valueOf(id), "SUCCESS", correlationId, null);
    }

    public List<Menu> menus() {
        return repository.findMenus();
    }

    @Transactional("businessTransactionManager")
    public long saveMenu(Menu menu, String actor, String correlationId) {
        long id = repository.saveMenu(menu);
        audit(actor, menu.id() == null ? "MENU_CREATE" : "MENU_UPDATE", "MENU",
                String.valueOf(id), "SUCCESS", correlationId, null);
        return id;
    }

    @Transactional("businessTransactionManager")
    public void deleteMenu(long id, String actor, String correlationId) {
        repository.deleteMenu(id);
        audit(actor, "MENU_DELETE", "MENU", String.valueOf(id), "SUCCESS", correlationId, null);
    }

    public PageResult<AuditEvent> audits(int page, int size) {
        return repository.findAudits(page, size);
    }

    public void audit(String actor, String action, String targetType, String targetId,
                      String result, String correlationId, String clientIp) {
        repository.appendAudit(new AuditEvent(null, actor, action, targetType, targetId,
                result, correlationId, clientIp, clock.instant()));
    }
}

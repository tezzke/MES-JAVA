CREATE TABLE sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username)
);

CREATE TABLE sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (code)
);

CREATE TABLE sys_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_permission_code (code)
);

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id)
        REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id)
        REFERENCES sys_role(id) ON DELETE CASCADE
);

CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id)
        REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT fk_sys_role_permission_permission FOREIGN KEY (permission_id)
        REFERENCES sys_permission(id) ON DELETE CASCADE
);

CREATE TABLE sys_menu (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT NULL,
    name VARCHAR(100) NOT NULL,
    path VARCHAR(255) NOT NULL,
    permission_code VARCHAR(100) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_menu_path (path),
    KEY ix_sys_menu_parent_sort (parent_id, sort_order),
    CONSTRAINT fk_sys_menu_parent FOREIGN KEY (parent_id)
        REFERENCES sys_menu(id) ON DELETE SET NULL
);

CREATE TABLE sys_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(100) NOT NULL,
    result VARCHAR(32) NOT NULL,
    correlation_id VARCHAR(100) NULL,
    client_ip VARCHAR(64) NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_sys_audit_occurred_at (occurred_at),
    KEY ix_sys_audit_target (target_type, target_id)
);

INSERT INTO sys_role(code, name) VALUES
('ADMIN', '系统管理员'),
('OPERATOR', '生产操作员');

INSERT INTO sys_permission(code, name) VALUES
('USER_READ', '查看用户'),
('USER_WRITE', '维护用户'),
('USER_AUTHORIZE', '分配用户角色'),
('ROLE_READ', '查看角色'),
('ROLE_WRITE', '维护角色'),
('ROLE_AUTHORIZE', '分配角色权限'),
('MENU_READ', '查看菜单'),
('MENU_WRITE', '维护菜单'),
('AUDIT_READ', '查看审计日志'),
('TELEMETRY_READ', '查看设备遥测与 3D'),
('ALARM_READ', '查看原始报警'),
('BARCODE_READ', '查看原始扫码记录');

INSERT INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'ADMIN';

INSERT INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'OPERATOR' AND p.code IN (
    'MENU_READ', 'TELEMETRY_READ', 'ALARM_READ', 'BARCODE_READ');

INSERT INTO sys_menu(parent_id, name, path, permission_code, sort_order, enabled) VALUES
(NULL, '用户管理', '/system/users', 'USER_READ', 10, TRUE),
(NULL, '角色管理', '/system/roles', 'ROLE_READ', 20, TRUE),
(NULL, '菜单管理', '/system/menus', 'MENU_READ', 30, TRUE),
(NULL, '审计日志', '/system/audits', 'AUDIT_READ', 40, TRUE);

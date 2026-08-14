INSERT INTO sys_permission(code, name) VALUES
('MODBUS_PROBE', '使用 Modbus 现场探针');

INSERT INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.code = 'ADMIN' AND p.code = 'MODBUS_PROBE';

INSERT INTO sys_menu(parent_id, name, path, permission_code, sort_order, enabled) VALUES
(NULL, 'Modbus 现场探针', '/system/modbus-probe', 'MODBUS_PROBE', 50, TRUE);

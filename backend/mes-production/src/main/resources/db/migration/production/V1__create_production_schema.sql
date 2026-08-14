CREATE TABLE md_master (
    id BIGINT NOT NULL AUTO_INCREMENT,
    type VARCHAR(32) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    reference_id BIGINT NULL,
    secondary_reference_id BIGINT NULL,
    sequence_no INT NULL,
    quantity DECIMAL(18, 4) NULL,
    attributes_json TEXT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_md_master_type_code (type, code),
    KEY ix_md_master_reference (type, reference_id)
);

CREATE TABLE prod_work_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    product_id BIGINT NOT NULL,
    route_id BIGINT NOT NULL,
    batch_no VARCHAR(100) NOT NULL,
    planned_quantity DECIMAL(18, 4) NOT NULL,
    good_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0,
    bad_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_prod_work_order_no (order_no),
    KEY ix_prod_work_order_batch (batch_no),
    CONSTRAINT ck_work_order_quantities CHECK (
        planned_quantity > 0 AND good_quantity >= 0 AND bad_quantity >= 0
        AND good_quantity + bad_quantity <= planned_quantity)
);

CREATE TABLE prod_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    work_order_id BIGINT NOT NULL,
    route_step_id BIGINT NOT NULL,
    operation_id BIGINT NOT NULL,
    station_id BIGINT NULL,
    sequence_no INT NOT NULL,
    planned_quantity DECIMAL(18, 4) NOT NULL,
    good_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0,
    bad_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_prod_task_order_step (work_order_id, route_step_id),
    KEY ix_prod_task_order_sequence (work_order_id, sequence_no),
    CONSTRAINT fk_prod_task_order FOREIGN KEY (work_order_id)
        REFERENCES prod_work_order(id) ON DELETE CASCADE,
    CONSTRAINT ck_prod_task_quantities CHECK (
        planned_quantity > 0 AND good_quantity >= 0 AND bad_quantity >= 0
        AND good_quantity + bad_quantity <= planned_quantity)
);

CREATE TABLE prod_trace (
    id BIGINT NOT NULL AUTO_INCREMENT,
    work_order_id BIGINT NOT NULL,
    task_id BIGINT NULL,
    batch_no VARCHAR(100) NOT NULL,
    barcode VARCHAR(200) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    good_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0,
    bad_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_prod_trace_batch_time (batch_no, occurred_at),
    KEY ix_prod_trace_barcode_time (barcode, occurred_at),
    CONSTRAINT fk_prod_trace_order FOREIGN KEY (work_order_id)
        REFERENCES prod_work_order(id),
    CONSTRAINT fk_prod_trace_task FOREIGN KEY (task_id)
        REFERENCES prod_task(id)
);

CREATE TABLE prod_idempotency (
    operation_code VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    result_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (operation_code, idempotency_key)
);

CREATE TABLE alarm_action (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_alarm_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    assignee VARCHAR(100) NULL,
    resolution VARCHAR(1000) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_alarm_action_source (source_alarm_id),
    KEY ix_alarm_action_status_time (status, updated_at)
);

INSERT IGNORE INTO sys_permission(code, name) VALUES
('MASTER_READ', '查看生产主数据'),
('MASTER_WRITE', '维护生产主数据'),
('PRODUCTION_READ', '查看工单与任务'),
('PRODUCTION_WRITE', '创建编辑工单'),
('PRODUCTION_RELEASE', '下达工单'),
('PRODUCTION_EXECUTE', '扫码与报工'),
('TRACE_READ', '查看生产追溯'),
('ALARM_ACTION_READ', '查看报警处置'),
('ALARM_ACTION_WRITE', '执行报警处置');

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'ADMIN' AND p.code IN (
    'MASTER_READ', 'MASTER_WRITE', 'PRODUCTION_READ', 'PRODUCTION_WRITE',
    'PRODUCTION_RELEASE', 'PRODUCTION_EXECUTE', 'TRACE_READ',
    'ALARM_ACTION_READ', 'ALARM_ACTION_WRITE');

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'OPERATOR' AND p.code IN (
    'MASTER_READ', 'PRODUCTION_READ', 'PRODUCTION_EXECUTE',
    'TRACE_READ', 'ALARM_ACTION_READ', 'ALARM_ACTION_WRITE');

INSERT INTO sys_menu(parent_id, name, path, permission_code, sort_order, enabled)
SELECT NULL, '生产主数据', '/production/master', 'MASTER_READ', 40, TRUE
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/production/master');

INSERT INTO sys_menu(parent_id, name, path, permission_code, sort_order, enabled)
SELECT NULL, '生产工单', '/production/work-orders', 'PRODUCTION_READ', 50, TRUE
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/production/work-orders');

INSERT INTO sys_menu(parent_id, name, path, permission_code, sort_order, enabled)
SELECT NULL, '生产追溯', '/production/trace', 'TRACE_READ', 60, TRUE
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/production/trace');

INSERT INTO sys_menu(parent_id, name, path, permission_code, sort_order, enabled)
SELECT NULL, '报警处置', '/alarm-actions', 'ALARM_ACTION_READ', 70, TRUE
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/alarm-actions');

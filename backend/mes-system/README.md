# mes-system

## 职责

系统管理与安全域：服务端 Session 登录/退出、CSRF 与 CORS、安全响应头、用户/角色/权限/菜单管理、账号锁定信息、审计记录、初始管理员引导，以及系统域 Flyway 迁移。

## 依赖规则

- 允许：`mes-core`、Spring Web/Security/Validation/JDBC、Flyway 和 MySQL 驱动。
- 禁止：依赖 acquisition、infrastructure、production、api；禁止访问 SQLite 遥测表。
- 生产域需要审计时依赖 core 的 `AuditRecorder`，不直接调用本模块内部仓储。

## 数据库归属

拥有 MySQL 的 `sys_user`、`sys_role`、`sys_permission`、关联表、`sys_menu`、
`sys_audit`，迁移位置为 `db/migration/system`，历史表为
`flyway_schema_history_system`。`V2` 新增 `MODBUS_PROBE` 并只赋予内置
`ADMIN` 角色。

## 公开契约

- HTTP：`/api/auth/*` 与 `/api/system-management/*`。
- 应用服务：`SystemApplicationService`。
- 模块内持久化端口：`SystemRepository`。
- 跨模块实现：`SystemAuditRecorder` 实现 core 的 `AuditRecorder`。
- 基础设施 Bean：具名业务数据源、JdbcTemplate 和事务管理器。

## 维护与扩展

新增受控操作时同时定义权限码、服务端 `@PreAuthorize`、管理员授权和必要审计；前端隐藏按钮不能替代服务端授权。数据库变更只能追加版本化 Flyway 脚本，不修改已上线迁移。引导管理员密码只能来自环境变量，禁止增加默认密码或写入日志。

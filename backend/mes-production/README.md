# mes-production

## 职责

生产业务域：产品/工序/工位/路线及路线步骤主数据，工单和路线任务，扫码开工、报工、完工，批次/条码追溯，以及对 SQLite 原始报警的业务处置。

## 依赖规则

- 允许：`mes-core`、Spring JDBC/Web/Security/Validation、Flyway。
- 禁止：依赖 acquisition、infrastructure、system、api；禁止直接查询 SQLite。
- 审计只通过 core 的 `AuditRecorder`；MySQL 连接复用具名业务数据源。

## 数据库归属

拥有 MySQL 的 `md_master`、`prod_work_order`、`prod_task`、`prod_trace`、`prod_idempotency`、`alarm_action`。迁移位置为 `db/migration/production`，历史表为 `flyway_schema_history_production`。`alarm_action.source_alarm_id` 只是 SQLite 报警 ID 引用，没有跨库外键。

## 公开契约

- REST：`/api/master-data/*`、`/api/production/*`、`/api/alarm-actions/*`。
- 应用服务：`MasterDataService`、`ProductionService`、`AlarmActionService`。
- 持久化端口：`ProductionRepository`。
- 状态机：`ProductionModels` 中的工单、任务和报警处置状态转换。

## 维护与扩展

业务转换必须先写入领域状态机并补测试，再由应用服务编排事务和审计。所有更新继续携带 `version` 做乐观锁校验；新增命令应考虑幂等键。不要在 Controller 复制状态规则，也不要通过跨库查询把遥测库耦合进业务事务。

# mes-infrastructure

## 职责

实现遥测处理与基础设施：SQLite 数据源和模式初始化、遥测/原始报警/扫码 JDBC 仓储、实时内存缓存、快照处理管道、阈值报警、批量写入与数据保留清理。

## 依赖规则

- 允许：`mes-core`、Spring JDBC/事务、SQLite JDBC。
- 禁止：依赖 acquisition、system、production、api；禁止持有 HTTP/WebSocket 实现或访问 MySQL 业务表。
- 向前端推送只调用 core 的 `RealtimeNotifier`，具体 WebSocket 实现在 api。

## 数据库归属

只拥有 SQLite 遥测库：`TelemetryRecords`、`AlarmRecords`、`BarcodeRecords` 及其索引。WAL 和表结构由 `MesSchemaInitializer` 初始化。系统用户、生产工单、审计和报警处置不属于本模块。

## 公开契约与实现

实现 core 的 `TelemetryRepository`、`AlarmRepository`、`BarcodeRepository` 和 `RealtimeCache`；提供 `TelemetryProcessingService`、`DataRetentionService` 以及具名 `telemetryDataSource`、`telemetryJdbcTemplate`、`telemetryTransactionManager`。

## 维护与扩展

修改 SQLite 模式时必须兼容已有文件并补充仓储测试；高频写入继续使用显式 SQL、批处理和短事务。替换时序数据库时应新增 core 端口实现，避免让采集层或 API 依赖数据库细节。不要把 MySQL 业务查询放入本模块。

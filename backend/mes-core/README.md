# mes-core

## 职责

领域共享内核，定义设备/点位配置、厂房与设备外观模型、遥测与报警实体、实时快照、后台任务基类，以及模块间端口。它不负责网络接入、数据库实现、HTTP 或 Spring 装配。

## 依赖规则

- 允许：JDK、`slf4j-api`；测试代码可使用测试依赖。
- 禁止：依赖其他 MES 模块、Spring Web/JDBC、安全框架、SQLite/MySQL 驱动或具体 Modbus 实现。
- 其他模块可以依赖 core；core 不反向引用适配器模块。

## 数据库归属

本模块不拥有数据库，也不包含 SQL。`TelemetryRecord`、`AlarmRecord`、`BarcodeRecord` 是遥测侧共享模型，不代表 core 负责持久化。

## 公开契约

- 采集与队列：`DeviceCollector`、`SnapshotQueue`。
- 存储：`TelemetryRepository`、`AlarmRepository`、`BarcodeRepository`。
- 实时能力：`RealtimeCache`、`RealtimeNotifier`。
- 跨域审计：`AuditRecorder`。
- 生命周期：`BackgroundService`。
- 厂房数字孪生：`plant` 包下的 `PlantLayout`（`plant.json` 根节点）、`PlantShell`、
  `AxisGrid`、`WallSegment`、`PlantZone`、`DeviceModel`、`ModelPart`。这些是纯配置模型，
  只描述几何与外观，不含任何渲染逻辑——渲染在前端，加载在 mes-api。

## 维护与扩展

只有多个模块都需要且与技术实现无关的稳定模型或端口才应加入 core。新增契约时先保持最小方法集，再由外层模块实现；不要为了复用工具代码把数据库 DTO、Controller DTO 或框架注解下沉到这里。

# mes-api

## 职责

应用启动模块与组合根。负责装配其余五个模块，提供设备、厂房模型、遥测、原始报警、扫码和
Modbus 现场探针 REST API，承载 WebSocket 实时推送、Swagger、前端静态资源、
设备档案与厂房模型加载，以及后台服务启停编排。

## 依赖规则

- 允许：依赖 `mes-core`、`mes-acquisition`、`mes-infrastructure`、`mes-system`、`mes-production` 和 Web/API 适配依赖。
- 禁止：让下层模块反向依赖 api；禁止在 Controller 中实现生产状态规则或直接拼接数据库 SQL。
- 本模块是具体实现装配点，不是共享模型存放处。

## 数据库归属

不拥有数据库模式。它通过装配使用 infrastructure 的 SQLite 遥测数据源，以及 system/production 的 MySQL 业务数据源。运行配置位于 `src/main/resources/application.yml`。

## 公开契约

- REST：`/api/devices`、`/api/plant`、`/api/telemetry`、`/api/alarms`、`/api/barcodes`、
  `/api/modbus-probe`、`/api/system`，以及 system/production 模块发布的管理接口。
  `/api/plant` 下发 3D 车间的厂房壳体与设备外观模型库，与 `/api/devices` 共用
  `TELEMETRY_READ` 权限；两者都是页面加载时取一次的静态骨架数据。
- WebSocket：`/hubs/realtime`，发送快照、报警和扫码 JSON 信封。
- 运维：`/api/system/health`（liveness）、`/api/system/readiness`（MySQL +
  SQLite readiness）、`/api/system/info`；非生产环境 Swagger 默认在 `/swagger`。
- 配置：`devices.json`（采集侧点表）、`plant.json`（展示侧厂房与设备外观）与
  `application.yml` 中声明的环境变量。两份 JSON 都按"`mes.*-config` 指定路径 →
  工作目录 → jar 内置"顺序查找（见 `JsonConfigLoader`），生产环境放在 jar 旁即可覆盖。

## 维护与扩展

新增用例优先放入所属领域模块，api 只做装配、协议转换和宿主配置。新增后台服务时明确启动顺序和反向停机行为；新增公开端点时同步安全规则、权限测试和文档。静态资源或 WebSocket 改动不得破坏 Session/CSRF/Origin 约束。

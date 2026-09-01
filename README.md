# MES 生产管理与设备监控系统（Java）

面向中小型产线的 MES：只读采集 ModbusTCP PLC 与网口扫码枪数据，提供生产主数据、工单/任务执行、批次条码追溯、报警处置、系统权限管理，以及实时看板、历史曲线和 Three.js 3D 车间。

系统使用双库：**MySQL 是业务库**（用户、角色、菜单、审计、主数据、工单、任务、生产追溯、报警处置），**SQLite 是遥测库**（设备遥测、原始报警、扫码记录）。不能只备份其中一个数据库。

详细说明见 [架构设计](docs/架构设计.md)、[操作手册](docs/操作手册.md)、
[Modbus 现场调试](docs/Modbus现场调试.md) 和 [部署与运维](docs/部署与运维.md)。

## 已实现能力

- Java 21 / Spring Boot 3，六个 Maven 模块：`mes-core`、`mes-acquisition`、`mes-infrastructure`、`mes-system`、`mes-production`、`mes-api`。
- 自研 ModbusTCP 03/04 只读采集、受限单设备现场探针、模拟采集、TCP 扫码枪、有界快照队列、断线重连。
- SQLite WAL 遥测存储、阈值报警、实时缓存、WebSocket 推送、历史数据保留。
- 基于服务端 Session 的登录，CSRF Cookie/Header 校验，权限码 RBAC，用户/角色/菜单管理与操作审计。
- MySQL Flyway 迁移；工单、任务和报警处置均执行代码中的状态机与乐观锁校验。
- Vue 3 管理端包含监控、3D、历史、原始报警/扫码、用户/角色/菜单/审计、主数据、工单任务、生产追溯和报警处置页面。

## 开发运行

环境要求：**JDK 21**（不要用 25/26）、项目内 Maven Wrapper 3.9.11、Node.js 20.19.x、MySQL 8。
IntelliJ 的项目 SDK 必须选 21；它不会自动写进 PowerShell 的 `JAVA_HOME`。
本机脚本只认项目级路径，查找顺序：`MES_JAVA_HOME` → `deploy/java-home.local` → 系统 `JAVA_HOME` / `PATH`（且必须是 21）。
复制 `deploy/java-home.local.example` 为 `deploy/java-home.local` 并写入本机 JDK 21 目录即可，该文件已忽略。

先创建 MySQL 数据库和最小权限账号，并设置环境变量。初始管理员密码**没有默认值**；首次空库启动时必须显式设置 `MES_BOOTSTRAP_ADMIN_PASSWORD`，创建后应立即轮换并从环境中移除。

```powershell
$env:MES_BUSINESS_DB_ENABLED = 'true'
$env:MES_BUSINESS_DB_URL = 'jdbc:mysql://localhost:3306/mes?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
$env:MES_BUSINESS_DB_USERNAME = 'mes_app'
$env:MES_BUSINESS_DB_PASSWORD = '<从安全存储读取>'
$env:MES_BOOTSTRAP_ADMIN_PASSWORD = '<仅首次启动使用的强随机密码>'

.\deploy\run-dev.ps1
```

另开终端启动前端：

```powershell
cd frontend
npm ci
npm run dev
```

开发地址为 `http://localhost:5173`，后端监听 `http://localhost:5100`，非生产环境 Swagger 位于 `/swagger`。完整变量和生产安全配置见 [部署与运维](docs/部署与运维.md)。

## 部署

- 容器：复制 `deploy/.env.example` 为未纳入版本控制的 `.env`，填入强随机秘密后运行 `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build`。
- Windows 目录发布：运行 `.\deploy\publish.ps1`；生成的 `start.bat` 只检查环境变量，不写入或回显密码。
- 双库备份与恢复使用 `deploy/backup.ps1` 和 `deploy/restore.ps1`，操作步骤见部署文档。
- Linux 内网离线交付见 [离线安装手册](deploy/OFFLINE-LINUX.md)，依赖缓存要求见 [离线依赖与构建](docs/离线依赖与构建.md)。

## 现场配置

现场只需维护两个 JSON，都支持放在 jar 同级目录覆盖 jar 内置档案，改完重启即可，无需重新构建：

- `devices.json` —— **采集侧**：采集模式、设备地址与从站号、寄存器点表、扫码枪来源 IP、3D 场景坐标。
  将 `Acquisition.Mode` 改为 `Modbus` 即切换到真实 PLC；采集端只读设备，不提供 PLC 写入能力。
- `plant.json` —— **展示侧**：3D 车间的厂房壳体（轴网、墙体、分区）与设备外观模型（机身尺寸、构件、配色）。
  厂房数据来自施工图，设备外观数据来自各设备技术协议书，每个模型都记录了尺寸出处。

两份文件拆开是因为责任人不同：点表归电气调试，厂房与外观归图纸与协议书。
`PlantConfigConsistencyTest` 会在构建阶段校验两者的引用完整性与坐标合理性，改完建议先跑一遍。
改法见 [操作手册](docs/操作手册.md) 9.2 节。

## 目录

```text
backend/    六模块后端（每个模块内有 README.md）
frontend/   Vue 3 + TypeScript 管理端，保留 Three.js 3D 车间
deploy/     镜像、Compose、发布和双库备份恢复脚本
docs/       架构、操作、部署运维文档
```

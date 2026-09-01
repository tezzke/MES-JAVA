# MES Linux amd64 HTTP 内网离线交付

## 约束

- 目标机需要 Linux amd64、Docker Engine 和 Docker Compose v2，不需要互联网。
  应用镜像内置 Eclipse Temurin **JRE 21**，目标机不必再装宿主机 JDK。
- Web 只由 Nginx 发布 HTTP 端口；应用的 5100 和 MySQL 3306 不发布。
- `SCANNER_PORT` 是宿主机扫码枪 TCP 端口，转发到容器内固定 6001。若修改
  `devices.json` 的 `Scanner.ListenPort`，需同步修改 compose 的容器端口。
- 会话与 CSRF Cookie 的 `Secure=false` 仅适用于可信 HTTP 内网。`MES_ALLOWED_ORIGIN`
  必须是浏览器实际访问的单一精确 Origin。

## 联网构建机

```bash
./deploy/linux/pack-offline.sh 1.0.0
sha256sum -c deploy/offline-dist/mes-1.0.0-linux-amd64.tar.gz.sha256
```

脚本测试并构建前后端，拉取固定 MySQL 镜像，校验三个镜像均为
`linux/amd64`，输出镜像 tar、文件 SHA256 清单、`VERSION`、记录实际镜像
content ID/digest 的 `IMAGE-MANIFEST.txt` 和最终交付 tar.gz。

## 首次安装

1. 将 tar.gz 与同名 `.sha256` 复制到目标机并校验、解压。
2. 复制 `.env.example` 到目标机安全路径，填写强密码和精确 HTTP Origin，权限设为 0600。
3. 按现场点表修改解压目录中的 `devices.json`；如厂房或设备尺寸与现场复测不一致，
   同时修改 `plant.json`（3D 车间的厂房壳体与设备外观，详见 `docs/操作手册.md` 9.2 节）。
4. 如需单设备现场调试，在环境文件中启用
   `MES_MODBUS_PROBE_ENABLED=true`，并把 `MES_MODBUS_PROBE_ALLOWED_CIDRS`
   收窄到设备网段；调试结束后关闭。详细字段与验收流程见项目文档
   `docs/Modbus现场调试.md`。
5. 执行：

```bash
sudo ./linux/install.sh --env /secure/path/mes.env
```

数据固定在 `/opt/mes/mysql`、`/opt/mes/telemetry`、`/opt/mes/config` 和
`/opt/mes/backups`。`devices.json` 与 `plant.json` 以只读方式挂入应用容器，
且只在首次安装时铺默认档案 —— 升级不会覆盖现场已调好的配置。首次登录并修改 admin
密码后，清空 `/opt/mes/config/mes.env` 中的 `MES_BOOTSTRAP_ADMIN_PASSWORD`，
再执行当前版本的 `upgrade.sh --skip-backup` 使容器重建。

## 运维

```bash
sudo /opt/mes/current/linux/backup.sh
sudo /opt/mes/current/linux/restore.sh --backup 20260814T090000Z-1.0.0 --confirm RESTORE
sudo ./linux/upgrade.sh
sudo /opt/mes/current/linux/rollback.sh
sudo /opt/mes/current/linux/rollback.sh --version 1.0.0
```

升级和回滚默认先生成 MySQL + SQLite 成套备份。恢复会再次生成安全备份、停止
Nginx/应用、校验并同时覆盖两套数据库。`--skip-backup` 只应在已有已验证备份时使用。

目标机所有启动路径都使用预载镜像：

```bash
cd /opt/mes/current
sudo env MES_VERSION="$(cat VERSION)" docker compose \
  --env-file /opt/mes/config/mes.env \
  -f docker-compose.offline.yml up -d --pull never --no-build
```

就绪检查为 `GET /api/system/readiness`，只有 MySQL 与 SQLite 均可执行只读查询时
才返回 200。原 `GET /api/system/health` 仍是独立 liveness。

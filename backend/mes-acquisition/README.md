# mes-acquisition

## 职责

负责设备边缘接入：自研 ModbusTCP 03/04 只读通道、寄存器解码、受限现场探针、真实/模拟采集器、采集调度、网口扫码枪监听，以及有界快照队列。设备连接失败在本层转换为离线快照并按配置重连。

## 依赖规则

- 允许：`mes-core`、用于组件装配的 Spring Context、JDK 网络与并发 API。
- 禁止：依赖 infrastructure、system、production、api；禁止直接访问 MySQL/SQLite、发布 HTTP 接口或写 PLC。
- 与处理层只通过 core 的 `SnapshotQueue`、模型和仓储端口协作。

## 数据库归属

本模块不拥有数据库。采集结果进入队列；扫码记录通过 core 仓储契约交给外部实现持久化。

## 公开契约与实现

主要实现包括 `ModbusTcpCollector`、`SimulatedCollector`、`ModbusTcpChannel`、
`RegisterDecoder`、`ModbusProbeService`、`ModbusProbeNetworkPolicy`、
`BoundedSnapshotQueue`、`AcquisitionHostedService` 和
`BarcodeScannerHostedService`。探针使用独立临时连接、CIDR/端口白名单、并发与
60 秒时限，不持久化临时配置。

## 维护与扩展

新增设备协议时实现 `DeviceCollector`，把连接、帧解析和重连封装在协议适配器内，并保持输出为 `DeviceSnapshot`。新增寄存器类型集中扩展解码器及测试。不得绕过队列直接调用处理服务，也不得把现场点表硬编码到 Java 类中。

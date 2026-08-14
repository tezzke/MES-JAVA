package com.mes.core.options;

import java.util.ArrayList;
import java.util.List;

/**
 * 单台设备的静态配置(来自 devices.json,不入库)。
 * 设备档案以配置文件为唯一数据源,便于版本管理与整体迁移。
 */
public class DeviceConfig {

    /** 设备唯一编码,如 "CNC-01"。作为全系统的设备主键。 */
    private String deviceId = "";

    /** 设备显示名称,如 "CNC加工中心1号"。 */
    private String name = "";

    /** 设备类型,如 "cnc" / "assembly",用于前端 3D 模型与图标选择。 */
    private String type = "";

    /** 所属产线/工段名称。 */
    private String line = "";

    /** 是否启用采集。停机检修的设备可置为 false。 */
    private boolean enabled = true;

    /** PLC / 网关的 IP 地址(模拟模式下不使用)。 */
    private String ipAddress = "127.0.0.1";

    /** ModbusTCP 端口,默认 502。 */
    private int port = 502;

    /** Modbus 从站号(Unit Id)。汇川 PLC 通常为 1。 */
    private int unitId = 1;

    /** 轮询周期(毫秒)。null 时使用全局默认值。 */
    private Integer pollIntervalMs;

    /** 历史数据入库间隔(秒):实时推送每个轮询周期都发,但入库按该间隔抽稀,避免数据库膨胀。 */
    private int storageIntervalSeconds = 5;

    /** 引用的点位模板名(见 {@link AcquisitionOptions#getPointTemplates()})。 */
    private String template;

    /** 设备自有的附加点位(会与模板点位合并)。 */
    private List<PointConfig> points = new ArrayList<>();

    /** 3D 车间场景中的摆放位置与朝向,前端据此渲染,布局调整无需改前端代码。 */
    private Position3D position = new Position3D();

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLine() {
        return line;
    }

    public void setLine(String line) {
        this.line = line;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getUnitId() {
        return unitId;
    }

    public void setUnitId(int unitId) {
        this.unitId = unitId;
    }

    public Integer getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(Integer pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public int getStorageIntervalSeconds() {
        return storageIntervalSeconds;
    }

    public void setStorageIntervalSeconds(int storageIntervalSeconds) {
        this.storageIntervalSeconds = storageIntervalSeconds;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public List<PointConfig> getPoints() {
        return points;
    }

    public void setPoints(List<PointConfig> points) {
        this.points = points;
    }

    public Position3D getPosition() {
        return position;
    }

    public void setPosition(Position3D position) {
        this.position = position;
    }
}

package com.mes.core.entity;

import com.mes.core.enums.AlarmLevel;

import java.time.Instant;

/**
 * 报警记录(数据库实体)。
 * 一条报警有完整的生命周期:触发(triggeredAt)→ 恢复(resolvedAt)。
 * resolvedAt 为 null 表示报警仍处于激活状态。
 */
public class AlarmRecord {

    /** 通讯类报警占用的虚拟点位编码。 */
    public static final String COMM_POINT = "__comm__";

    /** 设备状态字报警占用的虚拟点位编码。 */
    public static final String STATUS_POINT = "__status__";

    /** 自增主键。 */
    private long id;

    /** 设备编码。 */
    private String deviceId = "";

    /** 设备显示名(冗余存储,避免查询时反查配置)。 */
    private String deviceName = "";

    /** 触发报警的点位编码;通讯类报警为 {@link #COMM_POINT}。 */
    private String pointName = "";

    /** 报警等级。 */
    private AlarmLevel level = AlarmLevel.Info;

    /** 报警描述,如 "主轴温度 85.2℃ 超过高报阈值 80℃"。 */
    private String message = "";

    /** 触发时刻的数值。 */
    private double value;

    /** 触发时间(UTC 时刻)。 */
    private Instant triggeredAt = Instant.EPOCH;

    /** 恢复时间(UTC 时刻),null = 仍在报警。 */
    private Instant resolvedAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getPointName() {
        return pointName;
    }

    public void setPointName(String pointName) {
        this.pointName = pointName;
    }

    public AlarmLevel getLevel() {
        return level;
    }

    public void setLevel(AlarmLevel level) {
        this.level = level;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(Instant triggeredAt) {
        this.triggeredAt = triggeredAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}

package com.mes.core.entity;

import com.mes.core.enums.DataQuality;

import java.time.Instant;

/**
 * 遥测历史记录(数据库实体):一行 = 某设备某点位某时刻的一个工程值。
 * 采用窄表设计,新增点位无需改表结构;
 * 建有 (DeviceId, PointName, Timestamp) 联合索引以支撑历史曲线查询。
 */
public class TelemetryRecord {

    /** 自增主键。 */
    private long id;

    /** 设备编码。 */
    private String deviceId = "";

    /** 点位编码。 */
    private String pointName = "";

    /** 工程值。 */
    private double value;

    /** 数据质量(入库保留质量戳,便于事后核查)。 */
    private DataQuality quality = DataQuality.Good;

    /** 采集时间(UTC 时刻)。 */
    private Instant timestamp = Instant.EPOCH;

    public TelemetryRecord() {
    }

    public TelemetryRecord(String deviceId, String pointName, double value,
                           DataQuality quality, Instant timestamp) {
        this.deviceId = deviceId;
        this.pointName = pointName;
        this.value = value;
        this.quality = quality;
        this.timestamp = timestamp;
    }

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

    public String getPointName() {
        return pointName;
    }

    public void setPointName(String pointName) {
        this.pointName = pointName;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public DataQuality getQuality() {
        return quality;
    }

    public void setQuality(DataQuality quality) {
        this.quality = quality;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}

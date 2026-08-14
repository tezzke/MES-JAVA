package com.mes.core.model;

import com.mes.core.enums.DeviceStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备快照:一台设备一次轮询采集到的全部点位 + 状态。
 * 是采集层 → 处理层 → 前端(WebSocket)贯穿全链路的核心数据结构。
 */
public class DeviceSnapshot {

    /** 设备编码。 */
    private String deviceId = "";

    /** 设备显示名。 */
    private String deviceName = "";

    /** 是否在线(本次通讯是否成功)。 */
    private boolean online;

    /** 设备状态(离线/待机/运行/报警)。 */
    private DeviceStatus status = DeviceStatus.Offline;

    /** 采集时间戳(服务器 UTC 时刻)。 */
    private Instant timestamp = Instant.now();

    /** 全部点位的实时值。 */
    private List<PointValue> points = new ArrayList<>();

    /** 构造一台设备的离线快照(通讯失败时使用)。 */
    public static DeviceSnapshot offline(String deviceId, String deviceName) {
        DeviceSnapshot snapshot = new DeviceSnapshot();
        snapshot.deviceId = deviceId;
        snapshot.deviceName = deviceName;
        snapshot.online = false;
        snapshot.status = DeviceStatus.Offline;
        snapshot.timestamp = Instant.now();
        return snapshot;
    }

    /** 按点位编码查值,不存在返回 null。 */
    public PointValue getPoint(String name) {
        for (PointValue point : points) {
            if (point.getName().equals(name)) {
                return point;
            }
        }
        return null;
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

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public DeviceStatus getStatus() {
        return status;
    }

    public void setStatus(DeviceStatus status) {
        this.status = status;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public List<PointValue> getPoints() {
        return points;
    }

    public void setPoints(List<PointValue> points) {
        this.points = points;
    }
}

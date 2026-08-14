package com.mes.core.contract;

import com.mes.core.model.DeviceSnapshot;

import java.util.List;

/**
 * 实时数据缓存:保存每台设备最新一帧快照,供 REST API 直接读取,
 * 避免"查最新值"打到数据库。由处理层写入、API 层读取,线程安全。
 */
public interface RealtimeCache {

    /** 更新某设备的最新快照。 */
    void update(DeviceSnapshot snapshot);

    /** 获取某设备最新快照,无数据返回 null。 */
    DeviceSnapshot get(String deviceId);

    /** 获取全部设备的最新快照(按设备编码排序)。 */
    List<DeviceSnapshot> getAll();
}

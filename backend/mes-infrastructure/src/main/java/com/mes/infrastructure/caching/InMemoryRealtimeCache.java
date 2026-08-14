package com.mes.infrastructure.caching;

import com.mes.core.contract.RealtimeCache;
import com.mes.core.model.DeviceSnapshot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时数据缓存的默认实现:以设备编码为 key 的并发字典。
 * 处理层每处理一帧快照就覆盖更新;REST API 查询"当前值"时直接命中内存,零数据库压力。
 */
@Component
public class InMemoryRealtimeCache implements RealtimeCache {

    private final ConcurrentHashMap<String, DeviceSnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public void update(DeviceSnapshot snapshot) {
        snapshots.put(snapshot.getDeviceId(), snapshot);
    }

    @Override
    public DeviceSnapshot get(String deviceId) {
        return snapshots.get(deviceId);
    }

    @Override
    public List<DeviceSnapshot> getAll() {
        List<DeviceSnapshot> all = new ArrayList<>(snapshots.values());
        all.sort(Comparator.comparing(DeviceSnapshot::getDeviceId));
        return all;
    }
}

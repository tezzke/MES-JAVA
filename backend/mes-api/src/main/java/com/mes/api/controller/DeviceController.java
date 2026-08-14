package com.mes.api.controller;

import com.mes.api.controller.dto.DeviceMetaDto;
import com.mes.core.contract.RealtimeCache;
import com.mes.core.model.DeviceSnapshot;
import com.mes.core.options.AcquisitionOptions;
import com.mes.core.options.DeviceConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 设备接口:设备档案(来自配置文件)+ 实时快照(来自内存缓存)。
 */
@RestController
@RequestMapping("/api/devices")
@Tag(name = "设备", description = "设备档案与实时快照")
public class DeviceController {

    private final AcquisitionOptions options;
    private final RealtimeCache cache;

    public DeviceController(AcquisitionOptions options, RealtimeCache cache) {
        this.options = options;
        this.cache = cache;
    }

    /**
     * 获取全部设备档案:编码/名称/类型/产线/3D 位置/点位定义。
     * 前端 3D 场景与设备卡片均以此为骨架数据,只在页面加载时调用一次。
     */
    @GetMapping
    @Operation(summary = "设备档案列表")
    public List<DeviceMetaDto> getDevices() {
        List<DeviceConfig> devices = options.enabledDevices();
        return devices.stream()
                .map(device -> DeviceMetaDto.from(device, options.resolvePoints(device)))
                .toList();
    }

    /** 获取全部设备的最新实时快照(内存缓存,可高频调用)。 */
    @GetMapping("/snapshots")
    @Operation(summary = "全部设备最新快照")
    public List<DeviceSnapshot> getSnapshots() {
        return cache.getAll();
    }

    /** 获取单台设备的最新实时快照。 */
    @GetMapping("/{deviceId}/snapshot")
    @Operation(summary = "单台设备最新快照")
    public ResponseEntity<DeviceSnapshot> getSnapshot(@PathVariable String deviceId) {
        DeviceSnapshot snapshot = cache.get(deviceId);
        return snapshot == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(snapshot);
    }
}

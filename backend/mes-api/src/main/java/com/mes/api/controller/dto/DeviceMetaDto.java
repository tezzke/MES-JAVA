package com.mes.api.controller.dto;

import com.mes.core.options.DeviceConfig;
import com.mes.core.options.PointConfig;
import com.mes.core.options.Position3D;

import java.util.List;

/**
 * 设备档案(GET /api/devices):前端 3D 场景与设备卡片的骨架数据。
 *
 * @param deviceId  设备编码。
 * @param name      设备显示名。
 * @param type      设备类型(前端据此选 3D 模型与图标)。
 * @param line      所属产线/工段。
 * @param ipAddress PLC 地址(现场排查用)。
 * @param port      ModbusTCP 端口。
 * @param position  3D 场景中的位置与朝向。
 * @param points    点位定义列表。
 */
public record DeviceMetaDto(
        String deviceId,
        String name,
        String type,
        String line,
        String ipAddress,
        int port,
        Position3D position,
        List<PointMetaDto> points) {

    public static DeviceMetaDto from(DeviceConfig device, List<PointConfig> points) {
        return new DeviceMetaDto(
                device.getDeviceId(),
                device.getName(),
                device.getType(),
                device.getLine(),
                device.getIpAddress(),
                device.getPort(),
                device.getPosition(),
                points.stream().map(PointMetaDto::from).toList());
    }
}

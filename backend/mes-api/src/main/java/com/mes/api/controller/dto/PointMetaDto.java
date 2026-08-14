package com.mes.api.controller.dto;

import com.mes.core.options.PointConfig;

/**
 * 点位定义(设备档案的一部分),只暴露前端需要的字段 ——
 * 寄存器地址、功能码等现场调试信息不下发给浏览器。
 *
 * @param name        点位编码。
 * @param displayName 点位显示名。
 * @param unit        工程单位。
 * @param isStatus    是否为设备状态字点位。
 * @param isCounter   是否为累计产量点位。
 * @param alarmHigh   高报阈值,未配置为 null。
 * @param alarmLow    低报阈值,未配置为 null。
 */
public record PointMetaDto(
        String name,
        String displayName,
        String unit,
        boolean isStatus,
        boolean isCounter,
        Double alarmHigh,
        Double alarmLow) {

    public static PointMetaDto from(PointConfig point) {
        return new PointMetaDto(
                point.getName(),
                point.getDisplayName(),
                point.getUnit(),
                point.getIsStatus(),
                point.getIsCounter(),
                point.getAlarmHigh(),
                point.getAlarmLow());
    }
}

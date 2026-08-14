package com.mes.core.enums;

/**
 * 设备运行状态。
 * 与 PLC 中"状态寄存器"的数值约定保持一致(0~3),采集层读取到状态点位后按序号映射为本枚举。
 * <p>
 * 枚举常量刻意使用帕斯卡命名:序列化为 JSON 时直接输出 "Running" 这样的名称,
 * 与前端 TypeScript 的字面量联合类型一一对应,无需额外转换。
 */
public enum DeviceStatus {

    /** 离线:通讯失败或设备未上电。 */
    Offline,

    /** 待机:设备上电但未生产。 */
    Standby,

    /** 运行:正常生产中。 */
    Running,

    /** 报警:设备自身报警(由 PLC 状态字上报)。 */
    Alarm;

    /**
     * 按 PLC 状态字数值解析枚举;超出定义范围时按待机处理。
     *
     * @param code PLC 状态寄存器的数值。
     */
    public static DeviceStatus fromCode(int code) {
        DeviceStatus[] all = values();
        return code >= 0 && code < all.length ? all[code] : Standby;
    }
}

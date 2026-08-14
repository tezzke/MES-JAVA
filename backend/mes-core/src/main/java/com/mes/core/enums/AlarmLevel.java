package com.mes.core.enums;

/**
 * 报警等级,用于前端展示颜色与筛选。
 */
public enum AlarmLevel {

    /** 提示:不影响生产,仅记录。 */
    Info,

    /** 警告:数值接近限值或设备待机异常。 */
    Warning,

    /** 严重:超出报警阈值、设备报警、通讯断开等。 */
    Error;

    /** 按数据库中存放的序号还原枚举(越界时视为提示)。 */
    public static AlarmLevel fromOrdinal(int ordinal) {
        AlarmLevel[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : Info;
    }
}

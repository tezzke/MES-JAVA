package com.mes.core.enums;

/**
 * 数据质量标记。
 * 采集层与处理层都会对数据打质量戳,保证入库数据的可信度可追溯:
 * 前端与报表可以据此过滤掉坏点数据。
 */
public enum DataQuality {

    /** 正常:通讯成功且数值在合理量程内。 */
    Good,

    /** 可疑:数值超出配置的量程范围(可能是传感器漂移)。 */
    Uncertain,

    /** 坏点:通讯失败,数值不可用。 */
    Bad;

    /** 按数据库中存放的序号还原枚举(越界时视为坏点)。 */
    public static DataQuality fromOrdinal(int ordinal) {
        DataQuality[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : Bad;
    }
}

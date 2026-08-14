package com.mes.core.enums;

/**
 * Modbus 寄存器数据类型。
 * 决定一个点位占用几个寄存器(16bit 为一个寄存器)以及如何解码。
 * 汇川 PLC 默认使用大端字序(高字在前),解码逻辑见
 * {@code com.mes.acquisition.modbus.RegisterDecoder}。
 */
public enum RegisterDataType {

    /** 无符号 16 位整数,占 1 个寄存器。 */
    UInt16,

    /** 有符号 16 位整数,占 1 个寄存器。 */
    Int16,

    /** 无符号 32 位整数,占 2 个寄存器。 */
    UInt32,

    /** 有符号 32 位整数,占 2 个寄存器。 */
    Int32,

    /** IEEE754 单精度浮点,占 2 个寄存器。 */
    Float32;

    /** 该数据类型占用的寄存器个数。 */
    public int registerCount() {
        return this == UInt16 || this == Int16 ? 1 : 2;
    }
}

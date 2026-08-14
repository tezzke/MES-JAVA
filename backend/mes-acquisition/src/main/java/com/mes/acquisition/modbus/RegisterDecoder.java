package com.mes.acquisition.modbus;

import com.mes.core.enums.RegisterDataType;

/**
 * Modbus 寄存器解码器:把 16bit 寄存器数组按配置的数据类型解码为原始数值。
 * 字序约定:大端(高字在前),即 32 位值 = 寄存器[n] 为高 16 位、寄存器[n+1] 为低 16 位,
 * 与汇川 AM/H5U 系列 PLC 的默认字序一致;如现场设备字序不同,只需调整本类。
 */
public final class RegisterDecoder {

    private RegisterDecoder() {
    }

    /**
     * 从寄存器数组的 index 位置起,按 dataType 解码一个值。
     *
     * @param registers 已读取的寄存器块(元素为无符号 16 位值)。
     * @param index     该点位在块内的起始下标。
     * @param dataType  数据类型。
     */
    public static double decode(int[] registers, int index, RegisterDataType dataType) {
        return decode(registers, index, dataType, RegisterByteOrder.BIG_ENDIAN,
                RegisterWordOrder.HIGH_WORD_FIRST);
    }

    /**
     * 按指定的寄存器内字节序和 32 位字序解码。
     * 默认重载仍保持“大端字节 + 高字在前”，避免改变正式采集结果。
     */
    public static double decode(int[] registers, int index, RegisterDataType dataType,
                                RegisterByteOrder byteOrder, RegisterWordOrder wordOrder) {
        int required = dataType.registerCount();
        if (index < 0 || index + required > registers.length) {
            throw new IllegalArgumentException("寄存器下标越界:index=" + index + ", 块长度=" + registers.length);
        }

        int first = reorderBytes(registers[index], byteOrder);
        return switch (dataType) {
            case UInt16 -> first & 0xFFFF;
            case Int16 -> (short) first;
            case UInt32 -> combine(registers, index, byteOrder, wordOrder) & 0xFFFFFFFFL;
            case Int32 -> combine(registers, index, byteOrder, wordOrder);
            // 拼出 32 位原始位模式后按 IEEE754 解释为 float
            case Float32 -> Float.intBitsToFloat(combine(registers, index, byteOrder, wordOrder));
        };
    }

    /** 把相邻两个寄存器按配置拼成 32 位整数。 */
    private static int combine(int[] registers, int index, RegisterByteOrder byteOrder,
                               RegisterWordOrder wordOrder) {
        int first = reorderBytes(registers[index], byteOrder);
        int second = reorderBytes(registers[index + 1], byteOrder);
        int high = wordOrder == RegisterWordOrder.HIGH_WORD_FIRST ? first : second;
        int low = wordOrder == RegisterWordOrder.HIGH_WORD_FIRST ? second : first;
        return ((high & 0xFFFF) << 16) | (low & 0xFFFF);
    }

    private static int reorderBytes(int value, RegisterByteOrder byteOrder) {
        int unsigned = value & 0xFFFF;
        if (byteOrder == RegisterByteOrder.BIG_ENDIAN) {
            return unsigned;
        }
        return ((unsigned & 0xFF) << 8) | ((unsigned >>> 8) & 0xFF);
    }
}

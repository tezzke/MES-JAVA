package com.mes.acquisition.modbus;

import com.mes.core.enums.RegisterDataType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * 寄存器解码测试:字序与符号位是 Modbus 采集最常见的错误来源
 * (解码错了不会报错,只会静默产生错误数据),因此逐类型覆盖。
 */
class RegisterDecoderTest {

    @Test
    void decodesUnsignedAndSigned16Bit() {
        int[] registers = {0xFFFF};

        assertThat(RegisterDecoder.decode(registers, 0, RegisterDataType.UInt16)).isEqualTo(65535d);
        assertThat(RegisterDecoder.decode(registers, 0, RegisterDataType.Int16)).isEqualTo(-1d);
    }

    @Test
    void decodes32BitWithHighWordFirst() {
        // 0x0001_86A0 = 100000,高字在前
        int[] registers = {0x0001, 0x86A0};

        assertThat(RegisterDecoder.decode(registers, 0, RegisterDataType.UInt32)).isEqualTo(100000d);
        assertThat(RegisterDecoder.decode(registers, 0, RegisterDataType.Int32)).isEqualTo(100000d);
    }

    @Test
    void decodesNegative32BitAsSigned() {
        int[] registers = {0xFFFF, 0xFFFF};

        assertThat(RegisterDecoder.decode(registers, 0, RegisterDataType.Int32)).isEqualTo(-1d);
        // 同样的位模式按无符号解释是 2^32-1,说明两种类型必须严格区分
        assertThat(RegisterDecoder.decode(registers, 0, RegisterDataType.UInt32)).isEqualTo(4294967295d);
    }

    @Test
    void decodesIeee754Float() {
        // 36.5f = 0x42120000
        int[] registers = {0x4212, 0x0000};

        assertThat(RegisterDecoder.decode(registers, 0, RegisterDataType.Float32))
                .isCloseTo(36.5, within(1e-6));
    }

    @Test
    void decodesAtOffsetInsideBlock() {
        // 分块读取时点位位于块内偏移处
        int[] block = {0x0000, 0x0064, 0x0001, 0x86A0};

        assertThat(RegisterDecoder.decode(block, 1, RegisterDataType.UInt16)).isEqualTo(100d);
        assertThat(RegisterDecoder.decode(block, 2, RegisterDataType.UInt32)).isEqualTo(100000d);
    }

    @Test
    void rejectsOutOfRangeIndex() {
        int[] registers = {0x0001};

        // 32 位类型需要两个寄存器,块内只剩一个 → 必须显式失败而不是读到越界数据
        assertThatThrownBy(() -> RegisterDecoder.decode(registers, 0, RegisterDataType.UInt32))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void supportsByteAndWordOrderCombinations() {
        // 0x42120000(float 36.5) 的四种常见设备排列。
        assertThat(RegisterDecoder.decode(new int[]{0x4212, 0x0000}, 0,
                RegisterDataType.Float32, RegisterByteOrder.BIG_ENDIAN,
                RegisterWordOrder.HIGH_WORD_FIRST)).isCloseTo(36.5, within(1e-6));
        assertThat(RegisterDecoder.decode(new int[]{0x1242, 0x0000}, 0,
                RegisterDataType.Float32, RegisterByteOrder.LITTLE_ENDIAN,
                RegisterWordOrder.HIGH_WORD_FIRST)).isCloseTo(36.5, within(1e-6));
        assertThat(RegisterDecoder.decode(new int[]{0x0000, 0x4212}, 0,
                RegisterDataType.Float32, RegisterByteOrder.BIG_ENDIAN,
                RegisterWordOrder.LOW_WORD_FIRST)).isCloseTo(36.5, within(1e-6));
        assertThat(RegisterDecoder.decode(new int[]{0x0000, 0x1242}, 0,
                RegisterDataType.Float32, RegisterByteOrder.LITTLE_ENDIAN,
                RegisterWordOrder.LOW_WORD_FIRST)).isCloseTo(36.5, within(1e-6));
    }

    @Test
    void appliesByteOrderToSixteenBitValues() {
        assertThat(RegisterDecoder.decode(new int[]{0x3412}, 0, RegisterDataType.UInt16,
                RegisterByteOrder.LITTLE_ENDIAN, RegisterWordOrder.HIGH_WORD_FIRST))
                .isEqualTo(0x1234);
    }
}

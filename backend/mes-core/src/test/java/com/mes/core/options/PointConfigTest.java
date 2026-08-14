package com.mes.core.options;

import com.mes.core.enums.DataQuality;
import com.mes.core.enums.DeviceStatus;
import com.mes.core.enums.RegisterDataType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 点位地址计算与枚举映射测试。
 * 这些换算被寄存器分块、报警滞回和数据库读写共用,错一处影响面很大。
 */
class PointConfigTest {

    @Test
    void addressSpanFollowsDataType() {
        PointConfig point = new PointConfig();
        point.setAddress(100);

        point.setDataType(RegisterDataType.UInt16);
        assertThat(point.registerCount()).isEqualTo(1);
        assertThat(point.endAddress()).isEqualTo(101);

        point.setDataType(RegisterDataType.Float32);
        assertThat(point.registerCount()).isEqualTo(2);
        assertThat(point.endAddress()).isEqualTo(102);
    }

    @Test
    void deadbandDefaultsToTwoPercentOfThreshold() {
        PointConfig point = new PointConfig();

        // 未显式配置:按阈值绝对值的 2% 推算
        assertThat(point.deadbandFor(80)).isCloseTo(1.6, within(1e-9));
        assertThat(point.deadbandFor(-50)).isCloseTo(1.0, within(1e-9));

        point.setAlarmDeadband(5.0);
        assertThat(point.deadbandFor(80)).isEqualTo(5.0);
    }

    @Test
    void statusCodeMapsToDeviceStatus() {
        assertThat(DeviceStatus.fromCode(0)).isEqualTo(DeviceStatus.Offline);
        assertThat(DeviceStatus.fromCode(2)).isEqualTo(DeviceStatus.Running);
        // PLC 上报了约定外的数值时按待机处理,不能抛异常中断采集
        assertThat(DeviceStatus.fromCode(99)).isEqualTo(DeviceStatus.Standby);
    }

    @Test
    void qualityOrdinalRoundTrips() {
        for (DataQuality quality : DataQuality.values()) {
            assertThat(DataQuality.fromOrdinal(quality.ordinal())).isEqualTo(quality);
        }
        // 数据库里出现脏值时按坏点处理
        assertThat(DataQuality.fromOrdinal(-1)).isEqualTo(DataQuality.Bad);
    }
}

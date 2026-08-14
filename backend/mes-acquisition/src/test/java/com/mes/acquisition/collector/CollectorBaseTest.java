package com.mes.acquisition.collector;

import com.mes.core.enums.DataQuality;
import com.mes.core.enums.DeviceStatus;
import com.mes.core.model.DeviceSnapshot;
import com.mes.core.model.PointValue;
import com.mes.core.options.DeviceConfig;
import com.mes.core.options.PointConfig;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 采集基类公共流程测试:量程校验、状态字解析与通讯异常兜底。
 * 兜底行为是整套采集稳定性的基石 —— 采集失败必须变成"离线快照"而不是抛异常终结轮询循环。
 */
class CollectorBaseTest {

    /** 测试替身:直接返回预置的点位数值,或抛出预置异常模拟通讯失败。 */
    private static final class StubCollector extends CollectorBase {

        private final Map<String, Double> values;
        private final Exception failure;

        private StubCollector(DeviceConfig device, List<PointConfig> points,
                              Map<String, Double> values, Exception failure) {
            super(device, points, LoggerFactory.getLogger(StubCollector.class));
            this.values = values;
            this.failure = failure;
        }

        @Override
        protected Map<String, Double> readValues() throws Exception {
            if (failure != null) {
                throw failure;
            }
            return values;
        }
    }

    @Test
    void marksOutOfRangeValueAsUncertain() {
        PointConfig temperature = analog("Temperature", 0d, 100d);

        DeviceSnapshot snapshot = collect(List.of(temperature), Map.of("Temperature", 150d));

        PointValue value = snapshot.getPoint("Temperature");
        assertThat(value).isNotNull();
        // 超量程的数据照常入库与推送,但打上可疑质量戳,便于事后甄别传感器漂移
        assertThat(value.getQuality()).isEqualTo(DataQuality.Uncertain);
        assertThat(snapshot.isOnline()).isTrue();
    }

    @Test
    void marksInRangeValueAsGoodAndRoundsValue() {
        PointConfig temperature = analog("Temperature", 0d, 100d);

        DeviceSnapshot snapshot = collect(List.of(temperature), Map.of("Temperature", 36.56789d));

        PointValue value = snapshot.getPoint("Temperature");
        assertThat(value.getQuality()).isEqualTo(DataQuality.Good);
        // 保留 3 位小数,避免浮点噪声写入数据库
        assertThat(value.getValue()).isEqualTo(36.568d);
    }

    @Test
    void resolvesDeviceStatusFromStatusPoint() {
        PointConfig status = new PointConfig();
        status.setName("Status");
        status.setIsStatus(true);

        DeviceSnapshot snapshot = collect(List.of(status),
                Map.of("Status", (double) DeviceStatus.Alarm.ordinal()));

        assertThat(snapshot.getStatus()).isEqualTo(DeviceStatus.Alarm);
    }

    @Test
    void treatsDeviceWithoutStatusPointAsRunning() {
        DeviceSnapshot snapshot = collect(List.of(analog("Temperature", null, null)),
                Map.of("Temperature", 20d));

        assertThat(snapshot.getStatus()).isEqualTo(DeviceStatus.Running);
    }

    @Test
    void returnsOfflineSnapshotOnCommunicationFailure() {
        DeviceConfig device = device();
        StubCollector collector = new StubCollector(device, List.of(analog("Temperature", null, null)),
                Map.of(), new IOException("连接被拒绝"));

        DeviceSnapshot snapshot = collector.collect();

        // 通讯异常不外抛:轮询循环得以继续,离线状态由处理层转成通讯报警
        assertThat(snapshot.isOnline()).isFalse();
        assertThat(snapshot.getStatus()).isEqualTo(DeviceStatus.Offline);
        assertThat(snapshot.getDeviceId()).isEqualTo(device.getDeviceId());
        assertThat(snapshot.getPoints()).isEmpty();
    }

    private static DeviceSnapshot collect(List<PointConfig> points, Map<String, Double> values) {
        return new StubCollector(device(), points, values, null).collect();
    }

    private static DeviceConfig device() {
        DeviceConfig device = new DeviceConfig();
        device.setDeviceId("CNC-01");
        device.setName("CNC加工中心1号");
        return device;
    }

    private static PointConfig analog(String name, Double rangeMin, Double rangeMax) {
        PointConfig point = new PointConfig();
        point.setName(name);
        point.setDisplayName(name);
        point.setUnit("℃");
        point.setRangeMin(rangeMin);
        point.setRangeMax(rangeMax);
        return point;
    }
}

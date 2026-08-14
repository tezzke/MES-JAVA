package com.mes.core.options;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 设备档案解析逻辑测试:点位模板复用与设备启用过滤是现场配置最容易出错的两处,
 * 一旦解析错误会导致整台设备采集不到数据,因此单独覆盖。
 */
class AcquisitionOptionsTest {

    @Test
    void resolvePointsMergesTemplateAndDevicePoints() {
        PointConfig templatePoint = point("Temperature");
        PointConfig ownPoint = point("Extra");

        AcquisitionOptions options = new AcquisitionOptions();
        options.setPointTemplates(Map.of("cnc", List.of(templatePoint)));

        DeviceConfig device = new DeviceConfig();
        device.setDeviceId("CNC-01");
        device.setTemplate("cnc");
        device.setPoints(List.of(ownPoint));

        List<PointConfig> resolved = options.resolvePoints(device);

        // 模板点位在前、设备自有点位在后
        assertThat(resolved).extracting(PointConfig::getName)
                .containsExactly("Temperature", "Extra");
    }

    @Test
    void resolvePointsToleratesMissingTemplate() {
        AcquisitionOptions options = new AcquisitionOptions();

        DeviceConfig device = new DeviceConfig();
        device.setTemplate("not-exist");
        device.setPoints(List.of(point("Only")));

        // 模板名写错时只丢该模板的点位,不能让整台设备解析失败
        assertThat(options.resolvePoints(device)).extracting(PointConfig::getName)
                .containsExactly("Only");
    }

    @Test
    void enabledDevicesFiltersDisabledOnes() {
        DeviceConfig running = new DeviceConfig();
        running.setDeviceId("CNC-01");
        DeviceConfig maintenance = new DeviceConfig();
        maintenance.setDeviceId("CNC-02");
        maintenance.setEnabled(false);

        AcquisitionOptions options = new AcquisitionOptions();
        options.setDevices(List.of(running, maintenance));

        assertThat(options.enabledDevices()).extracting(DeviceConfig::getDeviceId)
                .containsExactly("CNC-01");
    }

    @Test
    void simulationModeIsCaseInsensitive() {
        AcquisitionOptions options = new AcquisitionOptions();

        options.setMode("simulation");
        assertThat(options.isSimulation()).isTrue();

        options.setMode("Modbus");
        assertThat(options.isSimulation()).isFalse();
    }

    private static PointConfig point(String name) {
        PointConfig config = new PointConfig();
        config.setName(name);
        return config;
    }
}

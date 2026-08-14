package com.mes.core.options;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据采集层总配置,绑定自 devices.json 的 "Acquisition" 节点。
 * 部署到现场时只需修改配置文件,无需改动任何代码:
 * <ul>
 *   <li>mode 切换 Simulation(假数据) / Modbus(真实 PLC);</li>
 *   <li>devices 增删设备、修改 IP 与寄存器表。</li>
 * </ul>
 */
public class AcquisitionOptions {

    /** 配置节点名称。 */
    public static final String SECTION_NAME = "Acquisition";

    /** 采集模式:"Simulation" = 模拟数据(开发测试用);"Modbus" = 真实 ModbusTCP 采集。 */
    private String mode = "Simulation";

    /** 全局默认轮询周期(毫秒),设备可单独覆盖。 */
    private int defaultPollIntervalMs = 1000;

    /** 通讯失败后的重连间隔(毫秒)。 */
    private int reconnectDelayMs = 5000;

    /** 单次读取的超时时间(毫秒)。 */
    private int readTimeoutMs = 3000;

    /**
     * 点位模板库:key = 模板名,value = 点位列表。
     * 同型号设备复用同一份寄存器表,避免配置文件重复膨胀。
     */
    private Map<String, List<PointConfig>> pointTemplates = new LinkedHashMap<>();

    /** 设备清单(本项目约 15 台)。 */
    private List<DeviceConfig> devices = new ArrayList<>();

    /** 是否为模拟模式。 */
    public boolean isSimulation() {
        return "Simulation".equalsIgnoreCase(mode);
    }

    /** 已启用采集的设备清单。 */
    public List<DeviceConfig> enabledDevices() {
        List<DeviceConfig> result = new ArrayList<>();
        for (DeviceConfig device : devices) {
            if (device.isEnabled()) {
                result.add(device);
            }
        }
        return result;
    }

    /**
     * 解析某台设备最终生效的点位列表:模板点位 + 设备自有点位。
     */
    public List<PointConfig> resolvePoints(DeviceConfig device) {
        List<PointConfig> points = new ArrayList<>();
        String template = device.getTemplate();
        if (template != null && !template.isEmpty()) {
            List<PointConfig> templatePoints = pointTemplates.get(template);
            if (templatePoints != null) {
                points.addAll(templatePoints);
            }
        }
        points.addAll(device.getPoints());
        return Collections.unmodifiableList(points);
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public int getDefaultPollIntervalMs() {
        return defaultPollIntervalMs;
    }

    public void setDefaultPollIntervalMs(int defaultPollIntervalMs) {
        this.defaultPollIntervalMs = defaultPollIntervalMs;
    }

    public int getReconnectDelayMs() {
        return reconnectDelayMs;
    }

    public void setReconnectDelayMs(int reconnectDelayMs) {
        this.reconnectDelayMs = reconnectDelayMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public Map<String, List<PointConfig>> getPointTemplates() {
        return pointTemplates;
    }

    public void setPointTemplates(Map<String, List<PointConfig>> pointTemplates) {
        this.pointTemplates = pointTemplates;
    }

    public List<DeviceConfig> getDevices() {
        return devices;
    }

    public void setDevices(List<DeviceConfig> devices) {
        this.devices = devices;
    }
}

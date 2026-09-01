package com.mes.core.plant;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 厂房数字孪生模型的根节点,绑定自 plant.json。
 * <p>
 * 为什么与 devices.json 分家:
 * <ul>
 *   <li>devices.json 是<b>采集</b>产物(IP / 从站号 / 寄存器点表),由现场电气调试决定;</li>
 *   <li>plant.json 是<b>展示</b>产物(厂房壳体 / 轴网 / 隔墙 / 设备外观),由建筑与设备图纸决定;</li>
 *   <li>两者变更频率与责任人都不同,拆开后改厂房不会碰坏点表,改点表也不会碰坏 3D 场景。</li>
 * </ul>
 * 设备与外观模型之间靠 {@code DeviceConfig.type} 做弱关联:设备档案只声明自己是什么型号,
 * 具体长什么样由本文件的 {@link #deviceModels} 决定,型号缺模型时前端退化为通用体块。
 */
public class PlantLayout {

    /** plant.json 中厂房壳体的节点名。 */
    public static final String SHELL_SECTION = "Plant";

    /** plant.json 中设备外观模型库的节点名。 */
    public static final String MODELS_SECTION = "DeviceModels";

    private PlantShell plant = new PlantShell();

    /** 设备外观模型库:key = 设备类型(DeviceConfig.type)。 */
    private Map<String, DeviceModel> deviceModels = new LinkedHashMap<>();

    /** 查询某类型的外观模型,未定义返回 null(由前端退化为通用体块)。 */
    public DeviceModel modelOf(String type) {
        return type == null ? null : deviceModels.get(type);
    }

    public PlantShell getPlant() {
        return plant;
    }

    public void setPlant(PlantShell plant) {
        this.plant = plant;
    }

    public Map<String, DeviceModel> getDeviceModels() {
        return deviceModels;
    }

    public void setDeviceModels(Map<String, DeviceModel> deviceModels) {
        this.deviceModels = deviceModels;
    }
}

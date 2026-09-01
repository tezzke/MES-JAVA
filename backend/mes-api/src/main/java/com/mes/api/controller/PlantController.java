package com.mes.api.controller;

import com.mes.core.plant.DeviceModel;
import com.mes.core.plant.PlantLayout;
import com.mes.core.plant.PlantShell;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 厂房模型接口:3D 车间的静态骨架数据(厂房壳体 + 设备外观模型库)。
 * <p>
 * 与设备接口分开的理由和配置文件分家一致 —— 厂房模型只在页面首次加载时取一次,
 * 而设备快照要高频轮询,两者放在一个接口里会让缓存策略互相牵制。
 */
@RestController
@RequestMapping("/api/plant")
@Tag(name = "厂房", description = "3D 车间的厂房壳体与设备外观模型")
public class PlantController {

    private final PlantLayout layout;

    public PlantController(PlantLayout layout) {
        this.layout = layout;
    }

    /** 完整厂房模型:壳体 + 轴网 + 隔墙 + 分区 + 设备外观模型库,页面加载时调一次。 */
    @GetMapping
    @Operation(summary = "厂房模型(壳体 + 设备外观模型库)")
    public PlantLayout getLayout() {
        return layout;
    }

    /** 只取厂房壳体,供不需要设备外观的场景(如二维平面图)使用。 */
    @GetMapping("/shell")
    @Operation(summary = "厂房壳体(轴网 / 隔墙 / 分区)")
    public PlantShell getShell() {
        return layout.getPlant();
    }

    /** 只取设备外观模型库,便于现场核对某型号的建模尺寸与协议书是否一致。 */
    @GetMapping("/models")
    @Operation(summary = "设备外观模型库")
    public Map<String, DeviceModel> getModels() {
        return layout.getDeviceModels();
    }
}

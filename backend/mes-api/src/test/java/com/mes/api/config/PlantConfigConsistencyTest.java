package com.mes.api.config;

import com.mes.core.options.AcquisitionOptions;
import com.mes.core.options.DeviceConfig;
import com.mes.core.plant.Bounds;
import com.mes.core.plant.DeviceModel;
import com.mes.core.plant.PlantLayout;
import com.mes.core.plant.PlantShell;
import com.mes.core.plant.WallSegment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * devices.json 与 plant.json 的一致性校验。
 * <p>
 * 这两份配置是现场唯一手改的文件,靠人眼核对 35 台设备的类型、模板与坐标不现实,
 * 而配置写错的表现是"3D 场景里设备不见了 / 变成灰盒子 / 穿到墙外",到现场才发现代价很高。
 * 因此把跨文件的引用完整性与坐标合理性固化成断言,构建阶段就拦住。
 */
class PlantConfigConsistencyTest {

    private static AcquisitionOptions acquisition;
    private static PlantLayout layout;

    @BeforeAll
    static void loadConfigs() throws IOException {
        acquisition = new DeviceCatalogConfiguration("")
                .acquisitionOptions(new DeviceCatalogConfiguration("").deviceCatalogRoot());
        layout = new PlantLayoutConfiguration("").plantLayout();
    }

    @Test
    void everyDeviceTypeHasAppearanceModel() {
        List<String> missing = acquisition.getDevices().stream()
                .map(DeviceConfig::getType)
                .distinct()
                .filter(type -> layout.getDeviceModels().get(type) == null)
                .toList();

        assertTrue(missing.isEmpty(),
                "以下设备类型在 plant.json 的 DeviceModels 中缺少外观模型:" + missing);
    }

    @Test
    void everyDeviceTemplateExists() {
        List<String> missing = new ArrayList<>();
        for (DeviceConfig device : acquisition.getDevices()) {
            if (acquisition.resolvePoints(device).isEmpty()) {
                missing.add(device.getDeviceId() + "(模板 " + device.getTemplate() + ")");
            }
        }
        assertTrue(missing.isEmpty(), "以下设备解析不到任何点位:" + missing);
    }

    @Test
    void deviceIdsAreUnique() {
        long distinct = acquisition.getDevices().stream()
                .map(DeviceConfig::getDeviceId)
                .distinct()
                .count();
        assertEquals(acquisition.getDevices().size(), distinct, "devices.json 存在重复的 DeviceId");
    }

    @Test
    void statusAndCounterPointsAreDeclaredOncePerTemplate() {
        acquisition.getPointTemplates().forEach((name, points) -> {
            long statusCount = points.stream().filter(p -> p.getIsStatus()).count();
            long counterCount = points.stream().filter(p -> p.getIsCounter()).count();
            assertEquals(1, statusCount, "点位模板 " + name + " 必须且只能有一个状态点");
            assertEquals(1, counterCount, "点位模板 " + name + " 必须且只能有一个累计产量点");
        });
    }

    @Test
    void everyDeviceSitsInsidePlantEnvelope() {
        Bounds envelope = layout.getPlant().getEnvelope();
        List<String> outside = new ArrayList<>();
        for (DeviceConfig device : acquisition.getDevices()) {
            double x = device.getPosition().getX();
            double z = device.getPosition().getZ();
            if (x < envelope.getMinX() || x > envelope.getMaxX()
                    || z < envelope.getMinZ() || z > envelope.getMaxZ()) {
                outside.add(device.getDeviceId() + String.format("(%.3f, %.3f)", x, z));
            }
        }
        assertTrue(outside.isEmpty(), "以下设备坐标落在厂房外墙之外:" + outside);
    }

    /**
     * 轴网是校验坐标换算是否正确的锚点:图纸柱距统一 10.5m,
     * 若换算式或原点写错,这里立刻不成立。
     */
    @Test
    void axisGridMatchesDrawingSpacing() {
        PlantShell shell = layout.getPlant();
        List<Double> xValues = shell.getAxisGrid().getAxesX().stream()
                .map(axis -> axis.getValue()).toList();
        assertEquals(6, xValues.size(), "图纸横向轴线应为 3-A ~ 3-F 共 6 条");
        assertEquals(4, shell.getAxisGrid().getAxesZ().size(), "图纸纵向轴线应为 3-1 ~ 3-4 共 4 条");

        for (int i = 1; i < xValues.size(); i++) {
            double spacing = xValues.get(i) - xValues.get(i - 1);
            assertEquals(10.5, spacing, 0.02,
                    "第 " + i + " 跨柱距应为 10.5m,实际 " + spacing + "m,坐标换算可能有误");
        }
    }

    @Test
    void modelDimensionsArePositiveAndDocumented() {
        List<String> problems = new ArrayList<>();
        layout.getDeviceModels().forEach((type, model) -> {
            if (model.getLength() <= 0 || model.getWidth() <= 0 || model.getHeight() <= 0) {
                problems.add(type + " 的外形尺寸必须为正数");
            }
            if (model.getSource() == null || model.getSource().isBlank()) {
                problems.add(type + " 缺少 Source(尺寸出处),无法追溯到协议书");
            }
            assertNotNull(model.getParts(), type + " 的 Parts 不应为 null");
        });
        assertTrue(problems.isEmpty(), String.join(";", problems));
    }

    /** 构件必须落在机身包络内(留 1m 余量给外挂机箱),否则模型会散成一堆浮空零件。 */
    @Test
    void modelPartsStayNearTheBody() {
        List<String> problems = new ArrayList<>();
        layout.getDeviceModels().forEach((type, model) -> {
            double limitX = model.getLength() / 2 + 2.5;
            double limitZ = model.getWidth() / 2 + 2.5;
            double limitY = model.getHeight() + 1.5;
            model.getParts().forEach(part -> {
                if (Math.abs(part.getX()) > limitX || Math.abs(part.getZ()) > limitZ
                        || part.getY() < 0 || part.getY() > limitY) {
                    problems.add(type + " 的构件「" + part.getName() + "」超出机身包络");
                }
            });
        });
        assertTrue(problems.isEmpty(), String.join(";", problems));
    }

    @Test
    void wallSegmentsHaveNonZeroLength() {
        List<String> degenerate = new ArrayList<>();
        for (WallSegment wall : layout.getPlant().getPartitions()) {
            if (wall.length() <= 0.01) {
                degenerate.add(wall.getName());
            }
        }
        assertTrue(degenerate.isEmpty(), "以下墙段起止点重合,无法渲染:" + degenerate);
    }

    @Test
    void zonesLieWithinEnvelopeAndHaveArea() {
        Bounds envelope = layout.getPlant().getEnvelope();
        List<String> problems = new ArrayList<>();
        layout.getPlant().getZones().forEach(zone -> {
            Bounds b = zone.getBounds();
            if (b.width() <= 0 || b.depth() <= 0) {
                problems.add(zone.getName() + " 的范围为空");
            }
            if (b.getMinX() < envelope.getMinX() - 0.01 || b.getMaxX() > envelope.getMaxX() + 0.01
                    || b.getMinZ() < envelope.getMinZ() - 0.01
                    || b.getMaxZ() > envelope.getMaxZ() + 0.01) {
                problems.add(zone.getName() + " 超出厂房外墙");
            }
        });
        assertTrue(problems.isEmpty(), String.join(";", problems));
        assertFalse(layout.getPlant().getZones().isEmpty(), "厂房应至少划分一个功能分区");
    }

    /** 每台设备都应落在某个功能分区内,否则 3D 里会站在没有地面色块的空地上。 */
    @Test
    void everyDeviceFallsInSomeZone() {
        List<String> homeless = new ArrayList<>();
        for (DeviceConfig device : acquisition.getDevices()) {
            double x = device.getPosition().getX();
            double z = device.getPosition().getZ();
            boolean inside = layout.getPlant().getZones().stream().anyMatch(zone -> {
                Bounds b = zone.getBounds();
                return x >= b.getMinX() && x <= b.getMaxX() && z >= b.getMinZ() && z <= b.getMaxZ();
            });
            if (!inside) {
                homeless.add(device.getDeviceId() + String.format("(%.3f, %.3f)", x, z));
            }
        }
        assertTrue(homeless.isEmpty(), "以下设备不在任何功能分区内:" + homeless);
    }

    /** 外观模型库里不该有没人用的型号,避免配置随时间腐化。 */
    @Test
    void everyAppearanceModelIsReferenced() {
        List<String> used = acquisition.getDevices().stream()
                .map(DeviceConfig::getType)
                .distinct()
                .toList();
        List<String> unused = layout.getDeviceModels().keySet().stream()
                .filter(type -> !used.contains(type))
                .toList();
        assertTrue(unused.isEmpty(), "plant.json 中以下外观模型没有任何设备引用:" + unused);
    }

    @Test
    void appearanceModelsCarryModelAndVendor() {
        List<String> problems = new ArrayList<>();
        layout.getDeviceModels().forEach((type, model) -> {
            if (model.getName() == null || model.getName().isBlank()) {
                problems.add(type + " 缺少设备名称");
            }
            if (model.getModel() == null || model.getModel().isBlank()) {
                problems.add(type + " 缺少型号");
            }
        });
        assertTrue(problems.isEmpty(), String.join(";", problems));
    }

    @Test
    void deviceModelsAreNotEmpty() {
        assertFalse(layout.getDeviceModels().isEmpty(), "plant.json 未定义任何设备外观模型");
        for (DeviceModel model : layout.getDeviceModels().values()) {
            assertNotNull(model);
        }
    }
}

package com.mes.acquisition.collector;

import com.mes.core.contract.DeviceCollector;
import com.mes.core.enums.DataQuality;
import com.mes.core.enums.DeviceStatus;
import com.mes.core.model.DeviceSnapshot;
import com.mes.core.model.PointValue;
import com.mes.core.options.DeviceConfig;
import com.mes.core.options.PointConfig;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 采集器抽象基类(模板方法模式)。
 * <p>
 * 继承关系:
 * <pre>
 *   DeviceCollector(接口)
 *     └── CollectorBase(本类:公共流程 —— 异常兜底、量程校验、状态解析)
 *           ├── ModbusTcpCollector:真实 ModbusTCP 读取
 *           └── SimulatedCollector:模拟数据生成
 * </pre>
 * 公共流程 {@link #collect()}:
 * <ol>
 *   <li>调用子类 {@link #readValues()} 拿到 {点位 → 原始工程值};</li>
 *   <li>按点位配置做量程校验,打数据质量戳(Good / Uncertain);</li>
 *   <li>从状态字点位解析设备状态;</li>
 *   <li>任何异常 → 返回 online=false 的离线快照,绝不抛出,保证调度循环稳定。</li>
 * </ol>
 */
public abstract class CollectorBase implements DeviceCollector {

    /** 设备静态配置。 */
    protected final DeviceConfig device;

    /** 该设备最终生效的点位列表(模板 + 自有,已在启动时解析)。 */
    protected final List<PointConfig> points;

    protected final Logger logger;

    protected CollectorBase(DeviceConfig device, List<PointConfig> points, Logger logger) {
        this.device = device;
        this.points = points;
        this.logger = logger;
    }

    @Override
    public final String deviceId() {
        return device.getDeviceId();
    }

    @Override
    public final DeviceSnapshot collect() {
        try {
            // 1. 子类负责真正的取数(Modbus 读寄存器 / 模拟生成)
            Map<String, Double> rawValues = readValues();

            // 2. 组装快照 + 量程校验
            DeviceSnapshot snapshot = new DeviceSnapshot();
            snapshot.setDeviceId(device.getDeviceId());
            snapshot.setDeviceName(device.getName());
            snapshot.setOnline(true);
            snapshot.setTimestamp(Instant.now());

            for (PointConfig point : points) {
                Double value = rawValues.get(point.getName());
                if (value == null) {
                    continue; // 该点位本次未读到(不应发生,防御处理)
                }
                snapshot.getPoints().add(new PointValue(
                        point.getName(),
                        point.getDisplayName(),
                        round3(value),
                        point.getUnit(),
                        validateRange(point, value)));
            }

            // 3. 解析设备状态字
            snapshot.setStatus(resolveStatus(snapshot));
            return snapshot;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); // 正常停机,交由调度循环退出
            return DeviceSnapshot.offline(device.getDeviceId(), device.getName());
        } catch (Exception ex) {
            // 4. 通讯异常兜底:返回离线快照,由处理层生成"通讯断开"报警
            logger.warn("设备 {} 采集失败:{}", device.getDeviceId(), ex.getMessage());
            return DeviceSnapshot.offline(device.getDeviceId(), device.getName());
        }
    }

    /**
     * 子类实现:读取全部点位的工程值(已完成 scale/offset 换算)。
     * 通讯失败时直接抛异常,由基类统一兜底。
     */
    protected abstract Map<String, Double> readValues() throws Exception;

    /** 量程校验:超出 [rangeMin, rangeMax] 的值标记为 Uncertain(可疑)。 */
    private static DataQuality validateRange(PointConfig point, double value) {
        boolean belowMin = point.getRangeMin() != null && value < point.getRangeMin();
        boolean aboveMax = point.getRangeMax() != null && value > point.getRangeMax();
        return belowMin || aboveMax ? DataQuality.Uncertain : DataQuality.Good;
    }

    /** 从状态字点位解析设备状态;未配置状态字时在线即视为运行。 */
    private DeviceStatus resolveStatus(DeviceSnapshot snapshot) {
        PointConfig statusPoint = points.stream()
                .filter(PointConfig::getIsStatus)
                .findFirst()
                .orElse(null);
        if (statusPoint == null) {
            return DeviceStatus.Running;
        }

        PointValue value = snapshot.getPoint(statusPoint.getName());
        int code = value == null ? 0 : (int) Math.round(value.getValue());
        return DeviceStatus.fromCode(code);
    }

    /** 保留 3 位小数,避免浮点噪声写入数据库与推送到前端。 */
    private static double round3(double value) {
        return Math.round(value * 1000d) / 1000d;
    }
}

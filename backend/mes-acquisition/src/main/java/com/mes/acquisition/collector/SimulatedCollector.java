package com.mes.acquisition.collector;

import com.mes.core.enums.DeviceStatus;
import com.mes.core.options.DeviceConfig;
import com.mes.core.options.PointConfig;
import org.slf4j.Logger;

import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 模拟采集器:开发测试阶段代替真实 PLC 生成"像真的一样"的假数据。
 * <p>
 * 模拟策略:
 * <ul>
 *   <li>内置一个简单的设备状态机:大部分时间"运行",周期性进入"待机",
 *       小概率触发"报警"或"离线",覆盖前端需要展示的全部状态;</li>
 *   <li>模拟量(温度/转速/电流等)= 基准值 + 正弦波 + 随机噪声,
 *       处于报警状态时会推高数值越过高报阈值,以便测试报警链路;</li>
 *   <li>产量计数器在"运行"状态下按节拍累加;</li>
 *   <li>以设备编码作为随机种子,保证每台设备曲线形态不同但可复现。</li>
 * </ul>
 * 切换到真实 PLC 时,只需把配置 Acquisition.Mode 改为 "Modbus",本类即不再使用。
 */
public final class SimulatedCollector extends CollectorBase {

    private final Random random;

    /** 每台设备错开的正弦相位。 */
    private final double phase;

    /** 累计产量。 */
    private double productCount;

    private DeviceStatus state = DeviceStatus.Running;
    private Instant nextStateChange = Instant.now();

    public SimulatedCollector(DeviceConfig device, List<PointConfig> points, Logger logger) {
        super(device, points, logger);
        // 用设备编码哈希做种子:同一设备每次运行的行为模式一致,便于复现问题
        this.random = new Random(device.getDeviceId().hashCode());
        this.phase = random.nextDouble() * Math.PI * 2;
        this.productCount = 1000 + random.nextInt(4000); // 初始产量,模拟设备已生产一段时间
    }

    @Override
    protected Map<String, Double> readValues() throws Exception {
        advanceStateMachine();

        // 模拟"离线":抛出异常走基类的离线兜底逻辑,和真实通讯失败路径完全一致
        if (state == DeviceStatus.Offline) {
            throw new SocketTimeoutException("模拟通讯超时(测试离线场景)");
        }

        double t = Instant.now().toEpochMilli() / 1000d;
        Map<String, Double> values = new HashMap<>();

        for (PointConfig point : points) {
            if (point.getIsStatus()) {
                values.put(point.getName(), (double) state.ordinal());
            } else if (point.getIsCounter()) {
                // 运行状态下产量累加,约每 4 个周期出一件
                if (state == DeviceStatus.Running && random.nextDouble() < 0.25) {
                    productCount += 1;
                }
                values.put(point.getName(), productCount);
            } else {
                values.put(point.getName(), simulateAnalog(point, t));
            }
        }

        return values;
    }

    /** 生成一个模拟量:基准 + 慢速正弦波 + 噪声,报警状态下越过高报阈值。 */
    private double simulateAnalog(PointConfig point, double t) {
        // 基准值与波动幅度:优先根据量程推算,否则用报警阈值推算
        double min = point.getRangeMin() != null ? point.getRangeMin() : 0;
        double max = point.getRangeMax() != null
                ? point.getRangeMax()
                : (point.getAlarmHigh() != null ? point.getAlarmHigh() : 100) * 1.2;
        double baseline = min + (max - min) * 0.45;
        double amplitude = (max - min) * 0.10;

        double value = baseline
                + amplitude * Math.sin(t / 40 + phase)              // 周期约 4 分钟的慢波动
                + amplitude * 0.3 * (random.nextDouble() - 0.5);    // 随机噪声

        if (state == DeviceStatus.Standby) {
            // 待机:数值回落到低位(转速/电流接近 0,温度缓慢下降)
            value = min + (value - min) * 0.15;
        } else if (state == DeviceStatus.Alarm && point.getAlarmHigh() != null) {
            // 报警:把数值推到高报阈值之上,触发处理层的阈值报警
            value = point.getAlarmHigh() * (1.02 + 0.05 * random.nextDouble());
        }

        return value;
    }

    /** 设备状态机:按概率在 运行/待机/报警/离线 之间切换。 */
    private void advanceStateMachine() {
        Instant now = Instant.now();
        if (now.isBefore(nextStateChange)) {
            return;
        }

        double dice = random.nextDouble();
        int durationSeconds;
        if (dice < 0.70) {                  // 70% 运行 1~3 分钟
            state = DeviceStatus.Running;
            durationSeconds = 60 + random.nextInt(120);
        } else if (dice < 0.88) {           // 18% 待机
            state = DeviceStatus.Standby;
            durationSeconds = 20 + random.nextInt(40);
        } else if (dice < 0.96) {           //  8% 报警
            state = DeviceStatus.Alarm;
            durationSeconds = 15 + random.nextInt(25);
        } else {                            //  4% 离线
            state = DeviceStatus.Offline;
            durationSeconds = 10 + random.nextInt(20);
        }
        nextStateChange = now.plusSeconds(durationSeconds);
    }
}

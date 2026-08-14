package com.mes.core.contract;

import com.mes.core.model.DeviceSnapshot;

/**
 * 设备采集器接口:一台设备对应一个采集器实例。
 * 实现类(继承关系见 docs/架构设计.md):
 * <pre>
 *   CollectorBase(抽象基类,公共采集流程)
 *     ├── ModbusTcpCollector —— 真实 ModbusTCP 采集(汇川 PLC)
 *     └── SimulatedCollector —— 模拟数据源(开发测试)
 * </pre>
 */
public interface DeviceCollector extends AutoCloseable {

    /** 该采集器负责的设备编码。 */
    String deviceId();

    /**
     * 执行一次完整采集,返回设备快照。
     * 约定:任何通讯异常都不向外抛出,而是返回 online=false 的快照,
     * 由处理层统一生成"通讯断开"报警,保证调度循环永不中断。
     */
    DeviceSnapshot collect();

    /** 释放底层连接;默认无资源可释放。 */
    @Override
    default void close() {
    }
}

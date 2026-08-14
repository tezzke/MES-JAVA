package com.mes.acquisition.collector;

import com.mes.acquisition.modbus.ModbusTcpChannel;
import com.mes.acquisition.modbus.RegisterDecoder;
import com.mes.core.options.DeviceConfig;
import com.mes.core.options.PointConfig;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ModbusTCP 采集器:通过 {@link ModbusTcpChannel} 从汇川 PLC 读取真实数据。
 * <p>
 * 性能优化 —— 寄存器分块批量读取:
 * 点位往往分散在多个地址,如果逐点读取,一台设备一个周期要发 N 条报文;
 * 本类在构造时把点位按功能码分组、按地址排序,把地址相近的点位合并为
 * 连续"读取块"(块内允许小间隙),一个块只发一条 Modbus 报文,
 * 把报文数从 N 条降到 2~3 条,显著缩短采集耗时。
 */
public final class ModbusTcpCollector extends CollectorBase {

    /** 单块最大寄存器数(Modbus 协议上限 125,留裕量)。 */
    private static final int MAX_BLOCK_SIZE = 120;

    /** 允许合并的最大地址间隙(寄存器数):间隙内的无用寄存器一并读出后丢弃。 */
    private static final int MAX_GAP = 10;

    private final ModbusTcpChannel channel;
    private final List<ReadBlock> blocks;

    public ModbusTcpCollector(DeviceConfig device, List<PointConfig> points,
                              int readTimeoutMs, Logger logger) {
        super(device, points, logger);
        this.channel = new ModbusTcpChannel(device.getIpAddress(), device.getPort(), readTimeoutMs, logger);
        this.blocks = buildReadBlocks(points);
    }

    @Override
    protected Map<String, Double> readValues() throws Exception {
        Map<String, Double> result = new HashMap<>();

        for (ReadBlock block : blocks) {
            // 一个块一条报文
            int[] registers = channel.readRegisters(
                    device.getUnitId(), block.functionCode, block.startAddress, block.count);

            for (PointConfig point : block.points) {
                int index = point.getAddress() - block.startAddress; // 点位在块内的偏移
                double raw = RegisterDecoder.decode(registers, index, point.getDataType());
                result.put(point.getName(), raw * point.getScale() + point.getOffset()); // 工程值换算
            }
        }

        return result;
    }

    /** 把点位合并为连续读取块。 */
    private static List<ReadBlock> buildReadBlocks(List<PointConfig> points) {
        List<ReadBlock> blocks = new ArrayList<>();

        // 按功能码分组(保持配置顺序),组内按地址升序合并
        Map<Integer, List<PointConfig>> groups = new LinkedHashMap<>();
        for (PointConfig point : points) {
            groups.computeIfAbsent(point.getFunctionCode(), key -> new ArrayList<>()).add(point);
        }

        for (Map.Entry<Integer, List<PointConfig>> group : groups.entrySet()) {
            List<PointConfig> sorted = new ArrayList<>(group.getValue());
            sorted.sort(Comparator.comparingInt(PointConfig::getAddress));

            ReadBlock current = null;
            for (PointConfig point : sorted) {
                int pointEnd = point.endAddress(); // 尾后地址

                // 无法并入当前块(间隙过大或超长)则另起新块
                boolean needNewBlock = current == null
                        || point.getAddress() - (current.startAddress + current.count) > MAX_GAP
                        || pointEnd - current.startAddress > MAX_BLOCK_SIZE;
                if (needNewBlock) {
                    current = new ReadBlock(group.getKey(), point.getAddress());
                    blocks.add(current);
                }

                current.count = Math.max(current.count, pointEnd - current.startAddress);
                current.points.add(point);
            }
        }

        return blocks;
    }

    @Override
    public void close() {
        channel.close();
    }

    /** 一次 Modbus 读取块:起始地址 + 长度 + 覆盖的点位。 */
    private static final class ReadBlock {

        private final int functionCode;
        private final int startAddress;
        private final List<PointConfig> points = new ArrayList<>();
        private int count;

        private ReadBlock(int functionCode, int startAddress) {
            this.functionCode = functionCode;
            this.startAddress = startAddress;
        }
    }
}

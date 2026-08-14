package com.mes.acquisition.hosting;

import com.mes.acquisition.collector.ModbusTcpCollector;
import com.mes.acquisition.collector.SimulatedCollector;
import com.mes.core.contract.DeviceCollector;
import com.mes.core.contract.SnapshotQueue;
import com.mes.core.hosting.BackgroundService;
import com.mes.core.model.DeviceSnapshot;
import com.mes.core.options.AcquisitionOptions;
import com.mes.core.options.DeviceConfig;
import com.mes.core.options.PointConfig;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 采集调度服务(后台常驻):数据采集层的入口。
 * <p>
 * 工作方式:
 * <ul>
 *   <li>启动时按配置为每台启用的设备创建采集器(模拟 / Modbus 二选一);</li>
 *   <li>每台设备独立一个轮询线程,互不干扰:某台设备通讯卡顿不会拖慢其他设备;</li>
 *   <li>每个周期:采集 → 快照写入 {@link SnapshotQueue} → 等待剩余节拍时间;</li>
 *   <li>设备离线时自动降频重试(按 reconnectDelayMs),避免高频重连风暴;</li>
 *   <li>停止时统一中断所有循环并释放采集器连接。</li>
 * </ul>
 */
@Component
@Order(30)
public class AcquisitionHostedService extends BackgroundService {

    private final AcquisitionOptions options;
    private final SnapshotQueue queue;
    private final List<DeviceCollector> collectors = new CopyOnWriteArrayList<>();

    public AcquisitionHostedService(AcquisitionOptions options, SnapshotQueue queue) {
        this.options = options;
        this.queue = queue;
    }

    @Override
    protected void execute() throws Exception {
        List<DeviceConfig> devices = options.enabledDevices();
        log.info("采集调度启动:模式={},设备数={}", options.getMode(), devices.size());
        if (devices.isEmpty()) {
            log.warn("devices.json 中没有启用的设备,采集调度空转");
            return;
        }

        AtomicInteger threadSeq = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(devices.size(), runnable -> {
            Thread thread = new Thread(runnable, "poll-" + threadSeq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });

        try {
            for (DeviceConfig device : devices) {
                DeviceCollector collector = createCollector(device);
                collectors.add(collector);
                pool.submit(() -> runDeviceLoop(device, collector));
            }

            // 主线程在此驻留,直到收到停机请求
            pool.shutdown();
            while (!isStopping()) {
                if (pool.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                    break;
                }
            }
        } finally {
            pool.shutdownNow();
            closeCollectors();
            log.info("采集调度已停止");
        }
    }

    /** 采集器工厂:根据全局模式选择模拟实现或真实 Modbus 实现。 */
    private DeviceCollector createCollector(DeviceConfig device) {
        List<PointConfig> points = options.resolvePoints(device);
        if (options.isSimulation()) {
            return new SimulatedCollector(device, points,
                    LoggerFactory.getLogger("Sim." + device.getDeviceId()));
        }
        return new ModbusTcpCollector(device, points, options.getReadTimeoutMs(),
                LoggerFactory.getLogger("Modbus." + device.getDeviceId()));
    }

    /** 单台设备的轮询循环。 */
    private void runDeviceLoop(DeviceConfig device, DeviceCollector collector) {
        int interval = device.getPollIntervalMs() != null
                ? device.getPollIntervalMs()
                : options.getDefaultPollIntervalMs();

        while (!isStopping()) {
            try {
                long startedAt = System.nanoTime();
                DeviceSnapshot snapshot = collector.collect(); // 内部已兜底,不会抛通讯异常
                queue.enqueue(snapshot);

                // 离线时按重连间隔降频,在线时补齐节拍剩余时间
                long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
                long waitMs = snapshot.isOnline()
                        ? Math.max(0, interval - elapsedMs)
                        : options.getReconnectDelayMs();
                if (!delay(waitMs)) {
                    break;
                }
            } catch (Exception ex) {
                // 最后一道防线:任何未预期异常都不允许终结轮询循环
                log.error("设备 {} 轮询循环出现未预期异常", device.getDeviceId(), ex);
                if (!delay(options.getReconnectDelayMs())) {
                    break;
                }
            }
        }
    }

    private void closeCollectors() {
        for (DeviceCollector collector : new ArrayList<>(collectors)) {
            try {
                collector.close();
            } catch (Exception ex) {
                log.warn("释放设备 {} 采集器失败:{}", collector.deviceId(), ex.toString());
            }
        }
        collectors.clear();
    }
}

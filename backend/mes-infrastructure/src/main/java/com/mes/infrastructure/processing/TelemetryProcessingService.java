package com.mes.infrastructure.processing;

import com.mes.core.contract.AlarmRepository;
import com.mes.core.contract.RealtimeCache;
import com.mes.core.contract.RealtimeNotifier;
import com.mes.core.contract.SnapshotQueue;
import com.mes.core.contract.TelemetryRepository;
import com.mes.core.entity.AlarmRecord;
import com.mes.core.entity.TelemetryRecord;
import com.mes.core.enums.AlarmLevel;
import com.mes.core.enums.DeviceStatus;
import com.mes.core.hosting.BackgroundService;
import com.mes.core.model.DeviceSnapshot;
import com.mes.core.model.PointValue;
import com.mes.core.options.AcquisitionOptions;
import com.mes.core.options.DeviceConfig;
import com.mes.core.options.PointConfig;
import com.mes.core.options.StorageOptions;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 数据处理管道(后台常驻):整个后端业务的中枢,消费 {@link SnapshotQueue} 中的设备快照。
 * <p>
 * 每帧快照依次经过 4 个处理环节:
 * <ol>
 *   <li>实时缓存 —— 覆盖更新 {@link RealtimeCache},供 REST 查询当前值;</li>
 *   <li>实时推送 —— 通过 {@link RealtimeNotifier}(WebSocket)推给前端;</li>
 *   <li>报警判定 —— 通讯断开 / 设备状态字报警 / 点位阈值越限,
 *       带激活态去重:同一报警持续期间只产生一条记录,恢复时回填 resolvedAt;</li>
 *   <li>抽稀入库 —— 按设备的 storageIntervalSeconds 抽稀,攒批(batchSize / flushIntervalMs)
 *       单事务批量写入,兼顾数据完整性与 SQLite 写入性能。</li>
 * </ol>
 * 稳定性:任何一帧处理失败只记日志、不中断管道;停机时执行最终 flush,保证缓冲数据不丢失。
 */
@Component
@Order(10)
public class TelemetryProcessingService extends BackgroundService {

    /** 队列空转时的等待粒度(毫秒):同时决定攒批超时的检查精度。 */
    private static final long POLL_TIMEOUT_MS = 200;

    private final SnapshotQueue queue;
    private final RealtimeCache cache;
    private final RealtimeNotifier notifier;
    private final TelemetryRepository telemetryRepository;
    private final AlarmRepository alarmRepository;
    private final StorageOptions storageOptions;

    /** 设备配置索引:deviceId → (设备配置, 生效点位)。启动时构建,只读。 */
    private final Map<String, DeviceEntry> deviceIndex = new LinkedHashMap<>();

    /** 入库缓冲区(仅本服务单线程访问,无需加锁)。 */
    private final List<TelemetryRecord> buffer = new ArrayList<>();

    /** 每台设备最近一次入库时间(抽稀用)。 */
    private final Map<String, Instant> lastStored = new HashMap<>();

    /** 激活报警表:key = "设备|点位|类型",value = 已入库的报警记录。 */
    private final Map<String, AlarmRecord> activeAlarms = new HashMap<>();

    /** 报警文案中的数值格式(与 .NET 版 "0.##" 一致)。 */
    private final DecimalFormat valueFormat = new DecimalFormat("0.##");

    private Instant lastFlush = Instant.now();

    public TelemetryProcessingService(SnapshotQueue queue,
                                      RealtimeCache cache,
                                      RealtimeNotifier notifier,
                                      TelemetryRepository telemetryRepository,
                                      AlarmRepository alarmRepository,
                                      StorageOptions storageOptions,
                                      AcquisitionOptions acquisitionOptions) {
        this.queue = queue;
        this.cache = cache;
        this.notifier = notifier;
        this.telemetryRepository = telemetryRepository;
        this.alarmRepository = alarmRepository;
        this.storageOptions = storageOptions;

        for (DeviceConfig device : acquisitionOptions.getDevices()) {
            deviceIndex.put(device.getDeviceId(),
                    new DeviceEntry(device, acquisitionOptions.resolvePoints(device)));
        }
    }

    @Override
    protected void execute() throws Exception {
        log.info("数据处理管道已启动");
        closeStaleAlarms();
        try {
            while (!isStopping()) {
                DeviceSnapshot snapshot = queue.poll(POLL_TIMEOUT_MS);
                if (snapshot == null) {
                    flushIfDue(); // 队列空闲也要按时把缓冲区落库
                    continue;
                }
                try {
                    processSnapshot(snapshot);
                } catch (Exception ex) {
                    // 单帧失败不允许中断整个管道
                    log.error("处理设备 {} 快照失败", snapshot.getDeviceId(), ex);
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); // 正常停机
        } finally {
            // 停机前把缓冲区剩余数据落库,保证不丢数
            try {
                flush();
                log.info("数据处理管道已停止,缓冲数据已全部落库");
            } catch (Exception ex) {
                log.error("停机落库失败,可能丢失 {} 条缓冲记录", buffer.size(), ex);
            }
        }
    }

    /**
     * 关闭上次运行遗留的未恢复报警。
     * 激活报警表只存在于内存,进程重启后无从判断旧报警此刻是否仍然成立;
     * 若不关闭,这些记录会永远挂在"当前激活报警"里,且条件再次成立时还会插入重复记录。
     * 现场条件如果确实还在,下一轮采集(1 秒内)就会重新触发一条新报警,信息不会丢。
     */
    private void closeStaleAlarms() {
        List<AlarmRecord> stale = alarmRepository.findActive();
        if (stale.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (AlarmRecord alarm : stale) {
            alarmRepository.resolve(alarm.getId(), now);
        }
        log.info("已关闭上次运行遗留的 {} 条未恢复报警", stale.size());
    }

    /** 处理一帧设备快照。 */
    private void processSnapshot(DeviceSnapshot snapshot) {
        // 1. 实时缓存
        cache.update(snapshot);

        // 2. 实时推送(WebSocket → 前端)
        notifier.pushSnapshots(List.of(snapshot));

        // 3. 报警判定
        evaluateAlarms(snapshot);

        // 4. 抽稀 + 攒批入库
        bufferForStorage(snapshot);
        if (buffer.size() >= storageOptions.getBatchSize()) {
            flush();
        } else {
            flushIfDue();
        }
    }

    // ==================== 入库 ====================

    /** 按设备入库间隔抽稀,把快照点位追加到缓冲区。 */
    private void bufferForStorage(DeviceSnapshot snapshot) {
        if (!snapshot.isOnline()) {
            return; // 离线帧没有有效数值,不入库(离线事件由报警记录承载)
        }

        DeviceEntry entry = deviceIndex.get(snapshot.getDeviceId());
        Duration interval = Duration.ofSeconds(
                entry != null ? entry.device().getStorageIntervalSeconds() : 5);

        Instant last = lastStored.get(snapshot.getDeviceId());
        if (last != null && Duration.between(last, snapshot.getTimestamp()).compareTo(interval) < 0) {
            return; // 未到入库节拍:实时推送不受影响,仅历史存储抽稀
        }
        lastStored.put(snapshot.getDeviceId(), snapshot.getTimestamp());

        for (PointValue point : snapshot.getPoints()) {
            buffer.add(new TelemetryRecord(
                    snapshot.getDeviceId(),
                    point.getName(),
                    point.getValue(),
                    point.getQuality(),
                    snapshot.getTimestamp()));
        }
    }

    /** 到达攒批超时则写库。 */
    private void flushIfDue() {
        long elapsedMs = Duration.between(lastFlush, Instant.now()).toMillis();
        if (elapsedMs >= storageOptions.getFlushIntervalMs()) {
            flush();
        }
    }

    /** 把缓冲区数据单事务批量写库。 */
    private void flush() {
        lastFlush = Instant.now();
        if (buffer.isEmpty()) {
            return;
        }

        List<TelemetryRecord> batch = new ArrayList<>(buffer);
        buffer.clear();
        telemetryRepository.insertBatch(batch);
        log.debug("批量入库 {} 条遥测记录", batch.size());
    }

    // ==================== 报警 ====================

    /**
     * 报警判定:对一帧快照检查三类报警,并维护激活/恢复的生命周期。
     */
    private void evaluateAlarms(DeviceSnapshot snapshot) {
        String deviceId = snapshot.getDeviceId();

        // ---- 类型 1:通讯断开 ----
        setAlarmState(
                deviceId + "|" + AlarmRecord.COMM_POINT + "|Comm",
                !snapshot.isOnline(),
                snapshot.isOnline(),
                () -> newAlarm(snapshot, AlarmRecord.COMM_POINT, AlarmLevel.Error, 0,
                        snapshot.getDeviceName() + " 通讯断开,设备离线"));

        // ---- 类型 2:设备状态字报警(PLC 自身上报) ----
        boolean deviceAlarm = snapshot.getStatus() == DeviceStatus.Alarm;
        setAlarmState(
                deviceId + "|" + AlarmRecord.STATUS_POINT + "|Device",
                deviceAlarm,
                !deviceAlarm,
                () -> newAlarm(snapshot, AlarmRecord.STATUS_POINT, AlarmLevel.Error, 0,
                        snapshot.getDeviceName() + " 设备报警(PLC 状态字上报)"));

        // ---- 类型 3:点位阈值越限 ----
        DeviceEntry entry = deviceIndex.get(deviceId);
        if (!snapshot.isOnline() || entry == null) {
            return;
        }

        for (PointConfig point : entry.points()) {
            PointValue current = snapshot.getPoint(point.getName());
            if (current == null) {
                continue;
            }
            double value = current.getValue();

            if (point.getAlarmHigh() != null) {
                // 滞回:越过高报阈值触发,回落到 (阈值 - 死区) 以下才恢复
                double high = point.getAlarmHigh();
                setAlarmState(
                        deviceId + "|" + point.getName() + "|High",
                        value > high,
                        value <= high - point.deadbandFor(high),
                        () -> newAlarm(snapshot, point.getName(), AlarmLevel.Error, value,
                                "%s %s %s%s 超过高报阈值 %s%s".formatted(
                                        snapshot.getDeviceName(), point.getDisplayName(),
                                        valueFormat.format(value), point.getUnit(),
                                        valueFormat.format(high), point.getUnit())));
            }

            if (point.getAlarmLow() != null) {
                // 滞回:跌破低报阈值触发,回升到 (阈值 + 死区) 以上才恢复
                double low = point.getAlarmLow();
                setAlarmState(
                        deviceId + "|" + point.getName() + "|Low",
                        value < low,
                        value >= low + point.deadbandFor(low),
                        () -> newAlarm(snapshot, point.getName(), AlarmLevel.Warning, value,
                                "%s %s %s%s 低于低报阈值 %s%s".formatted(
                                        snapshot.getDeviceName(), point.getDisplayName(),
                                        valueFormat.format(value), point.getUnit(),
                                        valueFormat.format(low), point.getUnit())));
            }
        }
    }

    /**
     * 报警状态机(去重 + 滞回核心):
     * <ul>
     *   <li>raise 成立 且 无激活报警 → 触发:入库 + 推送 + 记入激活表;</li>
     *   <li>clear 成立 且 有激活报警 → 恢复:回填 resolvedAt + 推送 + 移出激活表;</li>
     *   <li>两者都不成立(处于滞回死区内 / 持续报警 / 持续正常)→ 保持现状,避免报警刷屏。</li>
     * </ul>
     */
    private void setAlarmState(String key, boolean raise, boolean clear, Supplier<AlarmRecord> factory) {
        AlarmRecord existing = activeAlarms.get(key);

        if (raise && existing == null) {
            // 触发新报警
            AlarmRecord record = alarmRepository.insert(factory.get());
            activeAlarms.put(key, record);
            notifier.pushAlarm(record);
            log.warn("触发报警:{}", record.getMessage());
        } else if (clear && existing != null) {
            // 报警恢复
            Instant resolvedAt = Instant.now();
            existing.setResolvedAt(resolvedAt);
            alarmRepository.resolve(existing.getId(), resolvedAt);
            activeAlarms.remove(key);
            notifier.pushAlarm(existing);
            log.info("报警恢复:{}", existing.getMessage());
        }
    }

    private AlarmRecord newAlarm(DeviceSnapshot snapshot, String pointName,
                                 AlarmLevel level, double value, String message) {
        AlarmRecord record = new AlarmRecord();
        record.setDeviceId(snapshot.getDeviceId());
        record.setDeviceName(snapshot.getDeviceName());
        record.setPointName(pointName);
        record.setLevel(level);
        record.setValue(value);
        record.setMessage(message);
        record.setTriggeredAt(Instant.now());
        return record;
    }

    /** 设备配置索引条目。 */
    private record DeviceEntry(DeviceConfig device, List<PointConfig> points) {
    }
}

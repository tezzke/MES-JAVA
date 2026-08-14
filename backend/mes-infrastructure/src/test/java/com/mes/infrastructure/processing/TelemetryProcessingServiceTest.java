package com.mes.infrastructure.processing;

import com.mes.core.contract.AlarmRepository;
import com.mes.core.contract.PagedResult;
import com.mes.core.contract.RealtimeNotifier;
import com.mes.core.contract.SnapshotQueue;
import com.mes.core.contract.TelemetryRepository;
import com.mes.core.entity.AlarmRecord;
import com.mes.core.entity.BarcodeRecord;
import com.mes.core.entity.TelemetryRecord;
import com.mes.core.enums.AlarmLevel;
import com.mes.core.enums.DataQuality;
import com.mes.core.enums.DeviceStatus;
import com.mes.core.model.DeviceSnapshot;
import com.mes.core.model.PointValue;
import com.mes.core.options.AcquisitionOptions;
import com.mes.core.options.DeviceConfig;
import com.mes.core.options.PointConfig;
import com.mes.core.options.StorageOptions;
import com.mes.infrastructure.caching.InMemoryRealtimeCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 处理管道的报警语义测试(全链路最容易出问题的一块业务逻辑):
 * <ul>
 *   <li>越限只报一条(激活态去重),持续越限不刷屏;</li>
 *   <li>数值回落到死区内不算恢复,越过死区才回填恢复时间;</li>
 *   <li>离线帧产生通讯报警,恢复在线后自动恢复;</li>
 *   <li>在线帧的点位数据攒批落库。</li>
 * </ul>
 * 用手写测试替身而不是 Mock 框架:断言的是"最终状态",替身同时充当结果收集器,读起来更直观。
 */
class TelemetryProcessingServiceTest {

    private static final String DEVICE_ID = "CNC-01";
    private static final String POINT = "Temperature";

    private FakeQueue queue;
    private FakeAlarmRepository alarms;
    private FakeTelemetryRepository telemetry;
    private RecordingNotifier notifier;
    private TelemetryProcessingService service;

    @BeforeEach
    void setUp() {
        queue = new FakeQueue();
        alarms = new FakeAlarmRepository();
        telemetry = new FakeTelemetryRepository();
        notifier = new RecordingNotifier();

        StorageOptions storage = new StorageOptions();
        storage.setBatchSize(1);        // 每帧立即落库,便于断言
        storage.setFlushIntervalMs(50);

        service = new TelemetryProcessingService(queue, new InMemoryRealtimeCache(), notifier,
                telemetry, alarms, storage, options());
        service.start();
    }

    @AfterEach
    void tearDown() {
        service.stop(2000);
    }

    @Test
    void raisesThresholdAlarmOnceAndResolvesAfterDeadband() {
        // 1. 超过高报阈值(80)→ 触发一条报警
        queue.enqueue(snapshot(85));
        awaitUntil(() -> alarms.inserted.size() == 1);

        AlarmRecord alarm = alarms.inserted.get(0);
        assertThat(alarm.getLevel()).isEqualTo(AlarmLevel.Error);
        assertThat(alarm.getPointName()).isEqualTo(POINT);
        assertThat(alarm.getMessage()).contains("超过高报阈值");

        // 2. 持续越限 → 不产生第二条记录
        queue.enqueue(snapshot(90));
        awaitUntil(() -> telemetry.inserted.size() >= 1);
        assertThat(alarms.inserted).hasSize(1);

        // 3. 回落到死区内(阈值 80、死区 2 → 需低于 78 才算恢复)→ 仍不恢复
        queue.enqueue(snapshot(79));
        sleep(150);
        assertThat(alarms.resolvedIds).isEmpty();

        // 4. 越过死区 → 恢复并回填恢复时间
        queue.enqueue(snapshot(70));
        awaitUntil(() -> !alarms.resolvedIds.isEmpty());
        assertThat(alarms.resolvedIds).containsExactly(alarm.getId());
        assertThat(alarm.getResolvedAt()).isNotNull();

        // 报警推送两次:触发 + 恢复
        assertThat(notifier.alarms).hasSize(2);
    }

    @Test
    void raisesCommunicationAlarmForOfflineSnapshotAndResolvesOnRecovery() {
        queue.enqueue(DeviceSnapshot.offline(DEVICE_ID, "CNC加工中心1号"));
        awaitUntil(() -> alarms.inserted.size() == 1);

        AlarmRecord alarm = alarms.inserted.get(0);
        assertThat(alarm.getPointName()).isEqualTo(AlarmRecord.COMM_POINT);
        assertThat(alarm.getMessage()).contains("通讯断开");

        // 离线帧没有有效数值,不入库
        sleep(150);
        assertThat(telemetry.inserted).isEmpty();

        queue.enqueue(snapshot(30));
        awaitUntil(() -> !alarms.resolvedIds.isEmpty());
        assertThat(alarms.resolvedIds).containsExactly(alarm.getId());
    }

    @Test
    void buffersOnlinePointsIntoStorage() {
        queue.enqueue(snapshot(36.5));
        awaitUntil(() -> telemetry.inserted.size() == 1);

        TelemetryRecord record = telemetry.inserted.get(0);
        assertThat(record.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(record.getPointName()).isEqualTo(POINT);
        assertThat(record.getValue()).isEqualTo(36.5);
        assertThat(record.getQuality()).isEqualTo(DataQuality.Good);
    }

    // ==================== 测试数据 ====================

    private static AcquisitionOptions options() {
        PointConfig point = new PointConfig();
        point.setName(POINT);
        point.setDisplayName("主轴温度");
        point.setUnit("℃");
        point.setAlarmHigh(80d);
        point.setAlarmDeadband(2d); // 显式配置死区,避免依赖默认的 2% 推算

        DeviceConfig device = new DeviceConfig();
        device.setDeviceId(DEVICE_ID);
        device.setName("CNC加工中心1号");
        device.setStorageIntervalSeconds(0); // 每帧都入库,便于断言
        device.setPoints(List.of(point));

        AcquisitionOptions options = new AcquisitionOptions();
        options.setDevices(List.of(device));
        return options;
    }

    private static DeviceSnapshot snapshot(double value) {
        DeviceSnapshot snapshot = new DeviceSnapshot();
        snapshot.setDeviceId(DEVICE_ID);
        snapshot.setDeviceName("CNC加工中心1号");
        snapshot.setOnline(true);
        snapshot.setStatus(DeviceStatus.Running);
        snapshot.setTimestamp(Instant.now());
        snapshot.getPoints().add(new PointValue(POINT, "主轴温度", value, "℃", DataQuality.Good));
        return snapshot;
    }

    private static void awaitUntil(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            sleep(20);
        }
        throw new AssertionError("等待条件超时");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 测试替身 ====================

    private static final class FakeQueue implements SnapshotQueue {

        private final LinkedBlockingQueue<DeviceSnapshot> items = new LinkedBlockingQueue<>();

        @Override
        public void enqueue(DeviceSnapshot snapshot) {
            items.add(snapshot);
        }

        @Override
        public DeviceSnapshot poll(long timeoutMillis) throws InterruptedException {
            return items.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        }

        @Override
        public int size() {
            return items.size();
        }

        @Override
        public long droppedCount() {
            return 0;
        }
    }

    private static final class FakeAlarmRepository implements AlarmRepository {

        // 管道线程写、测试线程读,用并发集合保证可见性
        private final List<AlarmRecord> inserted = new CopyOnWriteArrayList<>();
        private final List<Long> resolvedIds = new CopyOnWriteArrayList<>();
        private final AtomicLong sequence = new AtomicLong();

        @Override
        public AlarmRecord insert(AlarmRecord alarm) {
            alarm.setId(sequence.incrementAndGet());
            inserted.add(alarm);
            return alarm;
        }

        @Override
        public void resolve(long alarmId, Instant resolvedAtUtc) {
            resolvedIds.add(alarmId);
        }

        @Override
        public List<AlarmRecord> findActive() {
            return List.of();
        }

        @Override
        public PagedResult<AlarmRecord> query(String deviceId, Instant fromUtc, Instant toUtc,
                                             int page, int pageSize) {
            return PagedResult.empty();
        }
    }

    private static final class FakeTelemetryRepository implements TelemetryRepository {

        private final List<TelemetryRecord> inserted = new CopyOnWriteArrayList<>();

        @Override
        public void insertBatch(List<TelemetryRecord> records) {
            inserted.addAll(records);
        }

        @Override
        public List<TelemetryRecord> query(String deviceId, String pointName,
                                           Instant fromUtc, Instant toUtc, int maxPoints) {
            return List.of();
        }

        @Override
        public int deleteBefore(Instant beforeUtc) {
            return 0;
        }
    }

    private static final class RecordingNotifier implements RealtimeNotifier {

        private final List<AlarmRecord> alarms = new CopyOnWriteArrayList<>();

        @Override
        public void pushSnapshots(List<DeviceSnapshot> snapshots) {
        }

        @Override
        public void pushAlarm(AlarmRecord alarm) {
            alarms.add(alarm);
        }

        @Override
        public void pushBarcode(BarcodeRecord barcode) {
        }
    }
}

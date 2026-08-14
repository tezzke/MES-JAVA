package com.mes.acquisition.pipeline;

import com.mes.core.contract.SnapshotQueue;
import com.mes.core.model.DeviceSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 有界快照队列({@link SnapshotQueue} 的默认实现)。
 * <p>
 * 数据流:采集器(多线程写入)→ [本队列] → TelemetryProcessingService(单线程消费)。
 * <p>
 * 稳定性设计:
 * <ul>
 *   <li>有界容量(默认 10000 帧),防止写库长时间阻塞导致内存无限增长;</li>
 *   <li>队列满时丢弃最旧的一帧、保住最新数据(现场更关心实时值),
 *       同时记录 Warning 日志,便于发现存储瓶颈;</li>
 *   <li>采集侧调用永不阻塞,写库慢绝不影响采集节拍。</li>
 * </ul>
 */
@Component
public class BoundedSnapshotQueue implements SnapshotQueue {

    /** 队列容量(帧)。15 台设备 1 秒一帧,可缓冲约 11 分钟的积压。 */
    private static final int CAPACITY = 10_000;

    private static final Logger log = LoggerFactory.getLogger(BoundedSnapshotQueue.class);

    private final LinkedBlockingDeque<DeviceSnapshot> queue = new LinkedBlockingDeque<>(CAPACITY);
    private final AtomicLong dropped = new AtomicLong();

    @Override
    public void enqueue(DeviceSnapshot snapshot) {
        while (!queue.offerLast(snapshot)) {
            queue.pollFirst(); // 丢最旧的一帧,腾位置给最新数据
            long total = dropped.incrementAndGet();
            if (total % 1000 == 1) {
                log.warn("快照队列已丢弃 {} 帧,请检查数据库写入性能", total);
            }
        }
    }

    @Override
    public DeviceSnapshot poll(long timeoutMillis) throws InterruptedException {
        return queue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public long droppedCount() {
        return dropped.get();
    }
}

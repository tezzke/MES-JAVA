package com.mes.acquisition.pipeline;

import com.mes.core.model.DeviceSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 有界队列行为测试:队列满时"丢最旧、留最新"是内存安全的兜底策略,
 * 必须保证既不阻塞采集线程、也不会无限增长。
 */
class BoundedSnapshotQueueTest {

    @Test
    void pollReturnsSnapshotsInFifoOrder() throws InterruptedException {
        BoundedSnapshotQueue queue = new BoundedSnapshotQueue();
        queue.enqueue(snapshot("A"));
        queue.enqueue(snapshot("B"));

        assertThat(queue.poll(10).getDeviceId()).isEqualTo("A");
        assertThat(queue.poll(10).getDeviceId()).isEqualTo("B");
        assertThat(queue.size()).isZero();
    }

    @Test
    void pollReturnsNullOnTimeoutInsteadOfBlockingForever() throws InterruptedException {
        BoundedSnapshotQueue queue = new BoundedSnapshotQueue();

        // 队列空时返回 null,处理管道据此去检查攒批超时
        assertThat(queue.poll(1)).isNull();
    }

    @Test
    void dropsOldestFrameWhenFull() throws InterruptedException {
        BoundedSnapshotQueue queue = new BoundedSnapshotQueue();

        int capacity = 10_000;
        for (int i = 0; i < capacity; i++) {
            queue.enqueue(snapshot("D-" + i));
        }
        assertThat(queue.droppedCount()).isZero();

        queue.enqueue(snapshot("newest"));

        assertThat(queue.size()).isEqualTo(capacity);
        assertThat(queue.droppedCount()).isEqualTo(1);
        // 被丢掉的是最旧的一帧,最新数据保留
        assertThat(queue.poll(10).getDeviceId()).isEqualTo("D-1");
    }

    private static DeviceSnapshot snapshot(String deviceId) {
        DeviceSnapshot snapshot = new DeviceSnapshot();
        snapshot.setDeviceId(deviceId);
        return snapshot;
    }
}

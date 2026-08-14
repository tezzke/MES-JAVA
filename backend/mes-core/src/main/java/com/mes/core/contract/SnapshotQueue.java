package com.mes.core.contract;

import com.mes.core.model.DeviceSnapshot;

/**
 * 快照队列:采集层(生产者)与数据处理层(消费者)之间的内存缓冲管道。
 * 作用:
 * <ol>
 *   <li>解耦 —— 采集线程只管往队列写,写库慢不会阻塞采集节拍;</li>
 *   <li>削峰 —— 数据库短暂繁忙时数据先积压在队列,保证不丢数;</li>
 *   <li>有界保护 —— 队列有容量上限,极端情况下丢弃最旧数据并记日志,防止内存耗尽。</li>
 * </ol>
 * 实现:{@code com.mes.acquisition.pipeline.BoundedSnapshotQueue}。
 */
public interface SnapshotQueue {

    /** 写入一条设备快照(采集层调用,永不阻塞)。 */
    void enqueue(DeviceSnapshot snapshot);

    /**
     * 取出一条快照(处理层调用);队列为空时最多等待 timeoutMillis 毫秒。
     *
     * @return 取到的快照;等待超时返回 null。
     * @throws InterruptedException 等待期间线程被中断(服务停机)。
     */
    DeviceSnapshot poll(long timeoutMillis) throws InterruptedException;

    /** 当前积压帧数,用于监控。 */
    int size();

    /** 累计因队列满而丢弃的帧数,用于监控存储瓶颈。 */
    long droppedCount();
}

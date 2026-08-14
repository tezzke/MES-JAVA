package com.mes.core.options;

/**
 * 存储层配置,绑定自 application.yml 的 "storage" 节点。
 */
public class StorageOptions {

    /**
     * 批量写库的最大条数:处理管道攒够 batchSize 条或到达 flushIntervalMs 即触发一次写库,
     * 用批量事务代替逐条插入,保证高频采集下数据库的写入性能。
     */
    private int batchSize = 200;

    /** 批量写库的最长等待时间(毫秒)。 */
    private int flushIntervalMs = 2000;

    /** 历史数据保留天数,超期由后台任务每日清理,防止磁盘写满。 */
    private int retentionDays = 90;

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getFlushIntervalMs() {
        return flushIntervalMs;
    }

    public void setFlushIntervalMs(int flushIntervalMs) {
        this.flushIntervalMs = flushIntervalMs;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }
}

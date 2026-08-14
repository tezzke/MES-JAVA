package com.mes.core.contract;

import com.mes.core.entity.TelemetryRecord;

import java.time.Instant;
import java.util.List;

/**
 * 遥测历史仓储:负责遥测数据的批量写入与历史查询。
 * 实现:{@code com.mes.infrastructure.repository.JdbcTelemetryRepository}。
 */
public interface TelemetryRepository {

    /** 批量插入遥测记录(单事务提交,保证写入性能与原子性)。 */
    void insertBatch(List<TelemetryRecord> records);

    /**
     * 查询历史曲线数据。
     *
     * @param maxPoints 返回点数上限,超过时按时间等距抽稀,防止前端一次拉取百万行。
     */
    List<TelemetryRecord> query(String deviceId, String pointName,
                                Instant fromUtc, Instant toUtc, int maxPoints);

    /** 删除指定时间之前的历史数据(数据保留策略),返回删除行数。 */
    int deleteBefore(Instant beforeUtc);
}

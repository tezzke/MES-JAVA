package com.mes.core.contract;

import com.mes.core.entity.AlarmRecord;

import java.time.Instant;
import java.util.List;

/**
 * 报警仓储。实现:{@code com.mes.infrastructure.repository.JdbcAlarmRepository}。
 */
public interface AlarmRepository {

    /** 插入新报警,返回带主键的实体。 */
    AlarmRecord insert(AlarmRecord alarm);

    /** 将报警标记为已恢复。 */
    void resolve(long alarmId, Instant resolvedAtUtc);

    /** 查询当前未恢复的激活报警。 */
    List<AlarmRecord> findActive();

    /** 分页查询历史报警(deviceId 为 null/空表示全部设备)。 */
    PagedResult<AlarmRecord> query(String deviceId, Instant fromUtc, Instant toUtc,
                                   int page, int pageSize);
}

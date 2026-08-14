package com.mes.infrastructure.repository;

import com.mes.core.contract.TelemetryRepository;
import com.mes.core.entity.TelemetryRecord;
import com.mes.core.enums.DataQuality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 遥测历史仓储实现(SQLite + JdbcTemplate)。
 */
@Repository
public class JdbcTelemetryRepository implements TelemetryRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcTelemetryRepository.class);

    private static final String SELECT_COLUMNS =
            "Id, DeviceId, PointName, \"Value\", Quality, Timestamp";

    /** 时间窗过滤条件,三个查询共用。 */
    private static final String RANGE_FILTER =
            "WHERE DeviceId = ? AND PointName = ? AND Timestamp >= ? AND Timestamp <= ?";

    private static final RowMapper<TelemetryRecord> MAPPER = (rs, rowNum) -> {
        TelemetryRecord record = new TelemetryRecord();
        record.setId(rs.getLong("Id"));
        record.setDeviceId(rs.getString("DeviceId"));
        record.setPointName(rs.getString("PointName"));
        record.setValue(rs.getDouble("Value"));
        record.setQuality(DataQuality.fromOrdinal(rs.getInt("Quality")));
        record.setTimestamp(Instant.ofEpochMilli(rs.getLong("Timestamp")));
        return record;
    };

    private final JdbcTemplate jdbc;

    public JdbcTelemetryRepository(@Qualifier("telemetryJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 批量插入:单事务 + JDBC 批处理,
     * 避免逐条插入时每条数据一次磁盘 fsync 的性能灾难。
     */
    @Override
    @Transactional("telemetryTransactionManager")
    public void insertBatch(List<TelemetryRecord> records) {
        if (records.isEmpty()) {
            return;
        }

        List<Object[]> args = new ArrayList<>(records.size());
        for (TelemetryRecord record : records) {
            args.add(new Object[]{
                    record.getDeviceId(),
                    record.getPointName(),
                    record.getValue(),
                    record.getQuality().ordinal(),
                    record.getTimestamp().toEpochMilli(),
            });
        }

        jdbc.batchUpdate("""
                INSERT INTO TelemetryRecords (DeviceId, PointName, "Value", Quality, Timestamp)
                VALUES (?, ?, ?, ?, ?)""", args);
    }

    @Override
    public List<TelemetryRecord> query(String deviceId, String pointName,
                                       Instant fromUtc, Instant toUtc, int maxPoints) {
        long from = fromUtc.toEpochMilli();
        long to = toUtc.toEpochMilli();

        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM TelemetryRecords " + RANGE_FILTER,
                Integer.class, deviceId, pointName, from, to);
        if (total == null || total == 0) {
            return List.of();
        }

        if (total <= maxPoints) {
            return jdbc.query("SELECT " + SELECT_COLUMNS + " FROM TelemetryRecords "
                            + RANGE_FILTER + " ORDER BY Timestamp",
                    MAPPER, deviceId, pointName, from, to);
        }

        // 数据量超过上限:按固定步长等距抽稀(第 1、1+stride、1+2*stride ... 行),
        // 用窗口函数在数据库端完成,避免把百万行拉进内存
        int stride = (int) Math.ceil(total / (double) maxPoints);
        log.debug("历史查询命中 {} 行,按步长 {} 抽稀到约 {} 点", total, stride, maxPoints);

        return jdbc.query("SELECT " + SELECT_COLUMNS
                        + " FROM ("
                        + "    SELECT *, ROW_NUMBER() OVER (ORDER BY Timestamp) AS rn"
                        + "    FROM TelemetryRecords " + RANGE_FILTER
                        + ") WHERE (rn - 1) % ? = 0 ORDER BY Timestamp",
                MAPPER, deviceId, pointName, from, to, stride);
    }

    @Override
    public int deleteBefore(Instant beforeUtc) {
        return jdbc.update("DELETE FROM TelemetryRecords WHERE Timestamp < ?", beforeUtc.toEpochMilli());
    }
}

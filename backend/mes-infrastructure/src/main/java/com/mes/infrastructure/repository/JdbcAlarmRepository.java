package com.mes.infrastructure.repository;

import com.mes.core.contract.AlarmRepository;
import com.mes.core.contract.PagedResult;
import com.mes.core.entity.AlarmRecord;
import com.mes.core.enums.AlarmLevel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 报警仓储实现(SQLite + JdbcTemplate)。
 */
@Repository
public class JdbcAlarmRepository implements AlarmRepository {

    private static final String SELECT_COLUMNS =
            "Id, DeviceId, DeviceName, PointName, Level, Message, \"Value\", TriggeredAt, ResolvedAt";

    /** 查询语句前缀,尾部保留空格以便直接拼接 WHERE 子句。 */
    private static final String SELECT_FROM = "SELECT " + SELECT_COLUMNS + " FROM AlarmRecords ";

    private static final RowMapper<AlarmRecord> MAPPER = (rs, rowNum) -> {
        AlarmRecord record = new AlarmRecord();
        record.setId(rs.getLong("Id"));
        record.setDeviceId(rs.getString("DeviceId"));
        record.setDeviceName(rs.getString("DeviceName"));
        record.setPointName(rs.getString("PointName"));
        record.setLevel(AlarmLevel.fromOrdinal(rs.getInt("Level")));
        record.setMessage(rs.getString("Message"));
        record.setValue(rs.getDouble("Value"));
        record.setTriggeredAt(Instant.ofEpochMilli(rs.getLong("TriggeredAt")));
        long resolvedAt = rs.getLong("ResolvedAt");
        record.setResolvedAt(rs.wasNull() ? null : Instant.ofEpochMilli(resolvedAt));
        return record;
    };

    private final JdbcTemplate jdbc;

    public JdbcAlarmRepository(@Qualifier("telemetryJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AlarmRecord insert(AlarmRecord alarm) {
        String sql = """
                INSERT INTO AlarmRecords
                    (DeviceId, DeviceName, PointName, Level, Message, "Value", TriggeredAt, ResolvedAt)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, alarm.getDeviceId());
            ps.setString(2, alarm.getDeviceName());
            ps.setString(3, alarm.getPointName());
            ps.setInt(4, alarm.getLevel().ordinal());
            ps.setString(5, alarm.getMessage());
            ps.setDouble(6, alarm.getValue());
            ps.setLong(7, alarm.getTriggeredAt().toEpochMilli());
            if (alarm.getResolvedAt() == null) {
                ps.setNull(8, java.sql.Types.INTEGER);
            } else {
                ps.setLong(8, alarm.getResolvedAt().toEpochMilli());
            }
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key != null) {
            alarm.setId(key.longValue()); // 主键回填,供后续恢复时更新
        }
        return alarm;
    }

    @Override
    public void resolve(long alarmId, Instant resolvedAtUtc) {
        jdbc.update("UPDATE AlarmRecords SET ResolvedAt = ? WHERE Id = ?",
                resolvedAtUtc.toEpochMilli(), alarmId);
    }

    @Override
    public List<AlarmRecord> findActive() {
        return jdbc.query(SELECT_FROM + "WHERE ResolvedAt IS NULL ORDER BY TriggeredAt DESC", MAPPER);
    }

    @Override
    public PagedResult<AlarmRecord> query(String deviceId, Instant fromUtc, Instant toUtc,
                                          int page, int pageSize) {
        boolean byDevice = deviceId != null && !deviceId.isEmpty();
        String filter = "WHERE TriggeredAt >= ? AND TriggeredAt <= ?" + (byDevice ? " AND DeviceId = ?" : "");

        List<Object> args = new ArrayList<>();
        args.add(fromUtc.toEpochMilli());
        args.add(toUtc.toEpochMilli());
        if (byDevice) {
            args.add(deviceId);
        }

        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM AlarmRecords " + filter, Integer.class, args.toArray());
        if (total == null || total == 0) {
            return PagedResult.empty();
        }

        List<Object> pagedArgs = new ArrayList<>(args);
        pagedArgs.add(pageSize);
        pagedArgs.add((long) (page - 1) * pageSize);

        List<AlarmRecord> items = jdbc.query(SELECT_FROM + filter
                + " ORDER BY TriggeredAt DESC LIMIT ? OFFSET ?", MAPPER, pagedArgs.toArray());
        return new PagedResult<>(total, items);
    }
}

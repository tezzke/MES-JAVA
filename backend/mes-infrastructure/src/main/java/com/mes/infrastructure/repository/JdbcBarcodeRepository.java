package com.mes.infrastructure.repository;

import com.mes.core.contract.BarcodeRepository;
import com.mes.core.contract.PagedResult;
import com.mes.core.entity.BarcodeRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 扫码记录仓储实现(SQLite + JdbcTemplate)。
 */
@Repository
public class JdbcBarcodeRepository implements BarcodeRepository {

    private static final String SELECT_COLUMNS = "Id, ScannerId, DeviceId, Barcode, ScannedAt";

    private static final RowMapper<BarcodeRecord> MAPPER = (rs, rowNum) -> {
        BarcodeRecord record = new BarcodeRecord();
        record.setId(rs.getLong("Id"));
        record.setScannerId(rs.getString("ScannerId"));
        record.setDeviceId(rs.getString("DeviceId"));
        record.setBarcode(rs.getString("Barcode"));
        record.setScannedAt(Instant.ofEpochMilli(rs.getLong("ScannedAt")));
        return record;
    };

    private final JdbcTemplate jdbc;

    public JdbcBarcodeRepository(@Qualifier("telemetryJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(BarcodeRecord record) {
        jdbc.update("""
                        INSERT INTO BarcodeRecords (ScannerId, DeviceId, Barcode, ScannedAt)
                        VALUES (?, ?, ?, ?)""",
                record.getScannerId(), record.getDeviceId(), record.getBarcode(),
                record.getScannedAt().toEpochMilli());
    }

    @Override
    public PagedResult<BarcodeRecord> query(String barcodeKeyword, String deviceId,
                                            Instant fromUtc, Instant toUtc, int page, int pageSize) {
        StringBuilder filter = new StringBuilder("WHERE ScannedAt >= ? AND ScannedAt <= ?");
        List<Object> args = new ArrayList<>();
        args.add(fromUtc.toEpochMilli());
        args.add(toUtc.toEpochMilli());

        if (barcodeKeyword != null && !barcodeKeyword.isEmpty()) {
            // 条码模糊匹配,支持只输入序列号片段进行追溯
            filter.append(" AND Barcode LIKE ?");
            args.add("%" + barcodeKeyword + "%");
        }
        if (deviceId != null && !deviceId.isEmpty()) {
            filter.append(" AND DeviceId = ?");
            args.add(deviceId);
        }

        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM BarcodeRecords " + filter, Integer.class, args.toArray());
        if (total == null || total == 0) {
            return PagedResult.empty();
        }

        List<Object> pagedArgs = new ArrayList<>(args);
        pagedArgs.add(pageSize);
        pagedArgs.add((long) (page - 1) * pageSize);

        List<BarcodeRecord> items = jdbc.query("SELECT " + SELECT_COLUMNS + " FROM BarcodeRecords "
                + filter + " ORDER BY ScannedAt DESC LIMIT ? OFFSET ?", MAPPER, pagedArgs.toArray());
        return new PagedResult<>(total, items);
    }

    @Override
    public List<BarcodeRecord> findRecent(int limit) {
        return jdbc.query("SELECT " + SELECT_COLUMNS
                + " FROM BarcodeRecords ORDER BY ScannedAt DESC LIMIT ?", MAPPER, limit);
    }
}

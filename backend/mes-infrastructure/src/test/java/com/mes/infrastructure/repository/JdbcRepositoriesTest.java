package com.mes.infrastructure.repository;

import com.mes.core.contract.PagedResult;
import com.mes.core.entity.AlarmRecord;
import com.mes.core.entity.BarcodeRecord;
import com.mes.core.entity.TelemetryRecord;
import com.mes.core.enums.AlarmLevel;
import com.mes.core.enums.DataQuality;
import com.mes.infrastructure.data.MesSchemaInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 三个仓储针对真实 SQLite(内存库)的读写测试。
 * <p>
 * 仓储层的 SQL 是拼出来的字符串,编译器管不着:列名写错、拼接少个空格、
 * 窗口函数语法不被 SQLite 支持 —— 这些都只有真正执行一次才会暴露,
 * 所以这里不用 Mock,直接建一个内存库跑完整链路。
 */
class JdbcRepositoriesTest {

    private static final String DEVICE_ID = "CNC-01";
    private static final String POINT = "SpindleTemp";
    private static final Instant BASE_TIME = Instant.parse("2026-08-12T00:00:00Z");

    private SingleConnectionDataSource dataSource;
    private JdbcTelemetryRepository telemetry;
    private JdbcAlarmRepository alarms;
    private JdbcBarcodeRepository barcodes;

    @BeforeEach
    void setUp() {
        // 单连接:SQLite 内存库随连接存在,连接断开数据即消失
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        dataSource.setDriverClassName("org.sqlite.JDBC");

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        new MesSchemaInitializer(jdbc).initialize();

        telemetry = new JdbcTelemetryRepository(jdbc);
        alarms = new JdbcAlarmRepository(jdbc);
        barcodes = new JdbcBarcodeRepository(jdbc);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    // ==================== 遥测 ====================

    @Test
    void telemetryRoundTripsValueAndQuality() {
        telemetry.insertBatch(List.of(
                record(0, 36.5, DataQuality.Good),
                record(1, 99.9, DataQuality.Uncertain)));

        List<TelemetryRecord> found = telemetry.query(DEVICE_ID, POINT,
                BASE_TIME, BASE_TIME.plus(Duration.ofHours(1)), 100);

        assertThat(found).hasSize(2);
        assertThat(found.get(0).getValue()).isEqualTo(36.5);
        assertThat(found.get(0).getQuality()).isEqualTo(DataQuality.Good);
        assertThat(found.get(1).getQuality()).isEqualTo(DataQuality.Uncertain);
        // 时间戳以毫秒存取,不能有精度丢失
        assertThat(found.get(0).getTimestamp()).isEqualTo(BASE_TIME);
    }

    @Test
    void telemetryQueryFiltersByDevicePointAndTimeRange() {
        telemetry.insertBatch(List.of(
                record(0, 1, DataQuality.Good),
                record(10, 2, DataQuality.Good)));
        telemetry.insertBatch(List.of(
                new TelemetryRecord("OTHER-01", POINT, 3, DataQuality.Good, BASE_TIME)));

        List<TelemetryRecord> found = telemetry.query(DEVICE_ID, POINT,
                BASE_TIME, BASE_TIME.plus(Duration.ofSeconds(5)), 100);

        // 只命中本设备本点位、且落在时间窗内的那一条
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getValue()).isEqualTo(1d);
    }

    @Test
    void telemetryQueryThinsOutWhenExceedingMaxPoints() {
        List<TelemetryRecord> batch = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            batch.add(record(i, i, DataQuality.Good));
        }
        telemetry.insertBatch(batch);

        List<TelemetryRecord> found = telemetry.query(DEVICE_ID, POINT,
                BASE_TIME, BASE_TIME.plus(Duration.ofMinutes(1)), 3);

        // 10 行按步长 4 抽稀 → 第 1、5、9 行(值 0、4、8),且仍按时间升序
        assertThat(found).extracting(TelemetryRecord::getValue)
                .containsExactly(0d, 4d, 8d);
    }

    @Test
    void telemetryDeleteBeforeRemovesExpiredRows() {
        telemetry.insertBatch(List.of(
                record(0, 1, DataQuality.Good),
                record(60, 2, DataQuality.Good)));

        int deleted = telemetry.deleteBefore(BASE_TIME.plus(Duration.ofSeconds(30)));

        assertThat(deleted).isEqualTo(1);
        assertThat(telemetry.query(DEVICE_ID, POINT, BASE_TIME, BASE_TIME.plus(Duration.ofHours(1)), 100))
                .hasSize(1);
    }

    // ==================== 报警 ====================

    @Test
    void alarmInsertReturnsGeneratedIdAndShowsUpAsActive() {
        AlarmRecord inserted = alarms.insert(alarm("主轴温度超限", BASE_TIME));

        assertThat(inserted.getId()).isPositive();

        List<AlarmRecord> active = alarms.findActive();
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getId()).isEqualTo(inserted.getId());
        assertThat(active.get(0).getLevel()).isEqualTo(AlarmLevel.Error);
        assertThat(active.get(0).getResolvedAt()).isNull();
        assertThat(active.get(0).getValue()).isEqualTo(85.5);
    }

    @Test
    void alarmResolveRemovesItFromActiveList() {
        AlarmRecord inserted = alarms.insert(alarm("主轴温度超限", BASE_TIME));
        Instant resolvedAt = BASE_TIME.plus(Duration.ofMinutes(5));

        alarms.resolve(inserted.getId(), resolvedAt);

        assertThat(alarms.findActive()).isEmpty();
        PagedResult<AlarmRecord> page = alarms.query(null,
                BASE_TIME.minus(Duration.ofHours(1)), BASE_TIME.plus(Duration.ofHours(1)), 1, 20);
        assertThat(page.items().get(0).getResolvedAt()).isEqualTo(resolvedAt);
    }

    @Test
    void alarmQueryPagesAndFiltersByDevice() {
        for (int i = 0; i < 5; i++) {
            alarms.insert(alarm("报警 " + i, BASE_TIME.plus(Duration.ofMinutes(i))));
        }
        AlarmRecord other = alarm("其他设备报警", BASE_TIME);
        other.setDeviceId("OTHER-01");
        alarms.insert(other);

        Instant from = BASE_TIME.minus(Duration.ofHours(1));
        Instant to = BASE_TIME.plus(Duration.ofHours(1));

        PagedResult<AlarmRecord> firstPage = alarms.query(DEVICE_ID, from, to, 1, 2);
        assertThat(firstPage.total()).isEqualTo(5);
        assertThat(firstPage.items()).hasSize(2);
        // 按触发时间倒序,第一页是最新的两条
        assertThat(firstPage.items().get(0).getMessage()).isEqualTo("报警 4");

        PagedResult<AlarmRecord> thirdPage = alarms.query(DEVICE_ID, from, to, 3, 2);
        assertThat(thirdPage.items()).hasSize(1);

        assertThat(alarms.query(null, from, to, 1, 20).total()).isEqualTo(6);
    }

    // ==================== 扫码 ====================

    @Test
    void barcodeQuerySupportsKeywordAndRecentLookup() {
        barcodes.insert(new BarcodeRecord("SIM-01", DEVICE_ID, "SN20260812-100001", BASE_TIME));
        barcodes.insert(new BarcodeRecord("SIM-01", DEVICE_ID, "SN20260812-100002",
                BASE_TIME.plus(Duration.ofSeconds(10))));
        barcodes.insert(new BarcodeRecord("SIM-02", "PACK-01", "XY-999",
                BASE_TIME.plus(Duration.ofSeconds(20))));

        Instant from = BASE_TIME.minus(Duration.ofHours(1));
        Instant to = BASE_TIME.plus(Duration.ofHours(1));

        // 条码片段模糊匹配
        assertThat(barcodes.query("100002", null, from, to, 1, 20).total()).isEqualTo(1);
        // 按工位过滤
        assertThat(barcodes.query(null, DEVICE_ID, from, to, 1, 20).total()).isEqualTo(2);
        // 最近记录按时间倒序
        assertThat(barcodes.findRecent(2)).extracting(BarcodeRecord::getBarcode)
                .containsExactly("XY-999", "SN20260812-100002");
    }

    // ==================== 测试数据 ====================

    private static TelemetryRecord record(int secondsOffset, double value, DataQuality quality) {
        return new TelemetryRecord(DEVICE_ID, POINT, value, quality,
                BASE_TIME.plus(Duration.ofSeconds(secondsOffset)));
    }

    private static AlarmRecord alarm(String message, Instant triggeredAt) {
        AlarmRecord record = new AlarmRecord();
        record.setDeviceId(DEVICE_ID);
        record.setDeviceName("CNC加工中心1号");
        record.setPointName(POINT);
        record.setLevel(AlarmLevel.Error);
        record.setMessage(message);
        record.setValue(85.5);
        record.setTriggeredAt(triggeredAt);
        return record;
    }
}

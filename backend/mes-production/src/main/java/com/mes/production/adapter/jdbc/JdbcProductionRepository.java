package com.mes.production.adapter.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mes.core.contract.PagedResult;
import com.mes.production.application.ProductionService;
import com.mes.production.contract.ProductionRepository;
import com.mes.production.domain.MasterData;
import com.mes.production.domain.ProductionModels.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 使用业务库 JdbcTemplate 的生产域 MySQL 适配器。 */
@Repository
public class JdbcProductionRepository implements ProductionRepository {
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() { };
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcProductionRepository(@Qualifier("businessJdbcTemplate") JdbcTemplate jdbc,
                                    ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public PagedResult<MasterData.Item> findMasterData(MasterData.Type type, int page, int size) {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM md_master WHERE type=?",
                Integer.class, type.name());
        List<MasterData.Item> items = jdbc.query(
                "SELECT * FROM md_master WHERE type=? ORDER BY code LIMIT ? OFFSET ?",
                (rs, row) -> mapMaster(rs), type.name(), size, (long) (page - 1) * size);
        return new PagedResult<>(total == null ? 0 : total, items);
    }

    @Override
    public Optional<MasterData.Item> findMasterData(long id) {
        return jdbc.query("SELECT * FROM md_master WHERE id=?", (rs, row) -> mapMaster(rs), id)
                .stream().findFirst();
    }

    @Override
    public long saveMasterData(MasterData.Item item) {
        String attributes = writeAttributes(item.attributes());
        if (item.id() == null) {
            return insertKey("""
                    INSERT INTO md_master(type,code,name,reference_id,secondary_reference_id,
                      sequence_no,quantity,attributes_json,enabled,version)
                    VALUES(?,?,?,?,?,?,?,?,?,0)""", item.type().name(), item.code(), item.name(),
                    item.referenceId(), item.secondaryReferenceId(), item.sequence(), item.quantity(),
                    attributes, item.enabled());
        }
        requireChanged(jdbc.update("""
                UPDATE md_master SET code=?,name=?,reference_id=?,secondary_reference_id=?,
                  sequence_no=?,quantity=?,attributes_json=?,enabled=?,version=version+1
                WHERE id=? AND type=? AND version=?""", item.code(), item.name(), item.referenceId(),
                item.secondaryReferenceId(), item.sequence(), item.quantity(), attributes,
                item.enabled(), item.id(), item.type().name(), item.version()));
        return item.id();
    }

    @Override
    public PagedResult<WorkOrder> findWorkOrders(int page, int size) {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM prod_work_order", Integer.class);
        List<WorkOrder> items = jdbc.query(
                "SELECT * FROM prod_work_order ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                (rs, row) -> mapOrder(rs), size, (long) (page - 1) * size);
        return new PagedResult<>(total == null ? 0 : total, items);
    }

    @Override
    public Optional<WorkOrder> findWorkOrder(long id) {
        return jdbc.query("SELECT * FROM prod_work_order WHERE id=?", (rs, row) -> mapOrder(rs), id)
                .stream().findFirst();
    }

    @Override
    public long insertWorkOrder(WorkOrder order) {
        return insertKey("""
                INSERT INTO prod_work_order(order_no,product_id,route_id,batch_no,planned_quantity,
                  good_quantity,bad_quantity,status,version,created_at) VALUES(?,?,?,?,?,0,0,?,0,?)""",
                order.orderNo(), order.productId(), order.routeId(), order.batchNo(),
                order.plannedQuantity(), order.status().name(), Timestamp.from(order.createdAt()));
    }

    @Override
    public void updateWorkOrder(WorkOrder order) {
        requireChanged(jdbc.update("""
                UPDATE prod_work_order SET order_no=?,product_id=?,route_id=?,batch_no=?,
                  planned_quantity=?,version=version+1 WHERE id=? AND version=? AND status='DRAFT'""",
                order.orderNo(), order.productId(), order.routeId(), order.batchNo(),
                order.plannedQuantity(), order.id(), order.version()));
    }

    @Override
    public void deleteDraftWorkOrder(long id, long version) {
        requireChanged(jdbc.update(
                "DELETE FROM prod_work_order WHERE id=? AND version=? AND status='DRAFT'",
                id, version));
    }

    @Override
    public void changeWorkOrderStatus(long id, long version, String fromStatus, String toStatus) {
        requireChanged(jdbc.update("""
                UPDATE prod_work_order SET status=?,version=version+1
                WHERE id=? AND version=? AND status=?""", toStatus, id, version, fromStatus));
    }

    @Override
    public int generateTasks(long workOrderId, long routeId, BigDecimal plannedQuantity) {
        List<MasterData.Item> steps = jdbc.query("""
                SELECT * FROM md_master WHERE type='ROUTE_STEP' AND reference_id=? AND enabled=TRUE
                ORDER BY sequence_no,id""", (rs, row) -> mapMaster(rs), routeId);
        for (MasterData.Item step : steps) {
            Long stationId = parseLong(step.attributes().get("stationId"));
            jdbc.update("""
                    INSERT INTO prod_task(work_order_id,route_step_id,operation_id,station_id,
                      sequence_no,planned_quantity,good_quantity,bad_quantity,status,version)
                    VALUES(?,?,?,?,?,?,0,0,'READY',0)""", workOrderId, step.id(),
                    step.secondaryReferenceId(), stationId, step.sequence(), plannedQuantity);
        }
        return steps.size();
    }

    @Override
    public List<ProductionTask> findTasks(long workOrderId) {
        return jdbc.query("SELECT * FROM prod_task WHERE work_order_id=? ORDER BY sequence_no,id",
                (rs, row) -> mapTask(rs), workOrderId);
    }

    @Override
    public Optional<ProductionTask> findTask(long taskId) {
        return jdbc.query("SELECT * FROM prod_task WHERE id=?", (rs, row) -> mapTask(rs), taskId)
                .stream().findFirst();
    }

    @Override
    public boolean allPriorTasksCompleted(long workOrderId, int sequence) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM prod_task
                WHERE work_order_id=? AND sequence_no<? AND status<>'COMPLETED'""",
                Integer.class, workOrderId, sequence);
        return count != null && count == 0;
    }

    @Override
    public boolean isFinalTask(long workOrderId, int sequence) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM prod_task WHERE work_order_id=? AND sequence_no>?""",
                Integer.class, workOrderId, sequence);
        return count != null && count == 0;
    }

    @Override
    public void changeTaskStatus(long taskId, long version, String fromStatus, String toStatus) {
        requireChanged(jdbc.update("""
                UPDATE prod_task SET status=?,version=version+1
                WHERE id=? AND version=? AND status=?""", toStatus, taskId, version, fromStatus));
    }

    @Override
    public void addReport(long taskId, long taskVersion, long workOrderId, long orderVersion,
                          BigDecimal good, BigDecimal bad, boolean updateWorkOrder) {
        requireChanged(jdbc.update("""
                UPDATE prod_task SET good_quantity=good_quantity+?,bad_quantity=bad_quantity+?,
                  version=version+1 WHERE id=? AND version=? AND status='IN_PROGRESS'
                  AND good_quantity+bad_quantity+?+?<=planned_quantity""",
                good, bad, taskId, taskVersion, good, bad));
        if (updateWorkOrder) {
            requireChanged(jdbc.update("""
                    UPDATE prod_work_order SET good_quantity=good_quantity+?,bad_quantity=bad_quantity+?,
                      version=version+1 WHERE id=? AND version=? AND status='IN_PROGRESS'
                      AND good_quantity+bad_quantity+?+?<=planned_quantity""",
                    good, bad, workOrderId, orderVersion, good, bad));
        }
    }

    @Override
    public boolean allTasksCompleted(long workOrderId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM prod_task WHERE work_order_id=? AND status<>'COMPLETED'",
                Integer.class, workOrderId);
        return count != null && count == 0;
    }

    @Override
    public void appendTrace(TraceEvent event) {
        jdbc.update("""
                INSERT INTO prod_trace(work_order_id,task_id,batch_no,barcode,event_type,
                  good_quantity,bad_quantity,occurred_at) VALUES(?,?,?,?,?,?,?,?)""",
                event.workOrderId(), event.taskId(), event.batchNo(), event.barcode(),
                event.eventType(), event.goodQuantity(), event.badQuantity(),
                Timestamp.from(event.occurredAt()));
    }

    @Override
    public PagedResult<TraceEvent> findTrace(String batchNo, String barcode, int page, int size) {
        String where = " WHERE (? IS NULL OR batch_no=?) AND (? IS NULL OR barcode=?)";
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM prod_trace" + where,
                Integer.class, batchNo, batchNo, barcode, barcode);
        List<TraceEvent> items = jdbc.query("SELECT * FROM prod_trace" + where
                        + " ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?",
                (rs, row) -> mapTrace(rs), batchNo, batchNo, barcode, barcode,
                size, (long) (page - 1) * size);
        return new PagedResult<>(total == null ? 0 : total, items);
    }

    @Override
    public Optional<Long> findIdempotentResult(String operation, String key) {
        return jdbc.query("SELECT result_id FROM prod_idempotency "
                        + "WHERE operation_code=? AND idempotency_key=?", (rs, row) -> {
                    long value = rs.getLong(1);
                    return rs.wasNull() ? null : value;
                }, operation, key).stream().findFirst();
    }

    @Override
    public boolean claimIdempotency(String operation, String key) {
        try {
            return jdbc.update("""
                    INSERT INTO prod_idempotency(operation_code,idempotency_key,created_at)
                    VALUES(?,?,CURRENT_TIMESTAMP)""", operation, key) == 1;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }

    @Override
    public void completeIdempotency(String operation, String key, long resultId) {
        requireChanged(jdbc.update("""
                UPDATE prod_idempotency SET result_id=?
                WHERE operation_code=? AND idempotency_key=?""", resultId, operation, key));
    }

    @Override
    public PagedResult<AlarmAction> findAlarmActions(int page, int size) {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM alarm_action", Integer.class);
        List<AlarmAction> items = jdbc.query(
                "SELECT * FROM alarm_action ORDER BY updated_at DESC LIMIT ? OFFSET ?",
                (rs, row) -> mapAlarm(rs), size, (long) (page - 1) * size);
        return new PagedResult<>(total == null ? 0 : total, items);
    }

    @Override
    public Optional<AlarmAction> findAlarmAction(long id) {
        return jdbc.query("SELECT * FROM alarm_action WHERE id=?", (rs, row) -> mapAlarm(rs), id)
                .stream().findFirst();
    }

    @Override
    public Optional<AlarmAction> findAlarmActionBySource(long sourceAlarmId) {
        return jdbc.query("SELECT * FROM alarm_action WHERE source_alarm_id=?",
                (rs, row) -> mapAlarm(rs), sourceAlarmId).stream().findFirst();
    }

    @Override
    public long insertAlarmAction(long sourceAlarmId) {
        return insertKey("""
                INSERT INTO alarm_action(source_alarm_id,status,version,created_at,updated_at)
                VALUES(?,'NEW',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)""", sourceAlarmId);
    }

    @Override
    public void updateAlarmAction(AlarmAction action, String targetStatus,
                                  String assignee, String resolution) {
        requireChanged(jdbc.update("""
                UPDATE alarm_action SET status=?,assignee=?,resolution=?,version=version+1,
                  updated_at=CURRENT_TIMESTAMP WHERE id=? AND version=? AND status=?""",
                targetStatus, assignee, resolution, action.id(), action.version(),
                action.status().name()));
    }

    private MasterData.Item mapMaster(ResultSet rs) throws SQLException {
        return new MasterData.Item(rs.getLong("id"), MasterData.Type.valueOf(rs.getString("type")),
                rs.getString("code"), rs.getString("name"), nullableLong(rs, "reference_id"),
                nullableLong(rs, "secondary_reference_id"), nullableInt(rs, "sequence_no"),
                rs.getBigDecimal("quantity"), readAttributes(rs.getString("attributes_json")),
                rs.getBoolean("enabled"), rs.getLong("version"));
    }

    private static WorkOrder mapOrder(ResultSet rs) throws SQLException {
        return new WorkOrder(rs.getLong("id"), rs.getString("order_no"), rs.getLong("product_id"),
                rs.getLong("route_id"), rs.getString("batch_no"), rs.getBigDecimal("planned_quantity"),
                rs.getBigDecimal("good_quantity"), rs.getBigDecimal("bad_quantity"),
                WorkOrderStatus.valueOf(rs.getString("status")), rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant());
    }

    private static ProductionTask mapTask(ResultSet rs) throws SQLException {
        return new ProductionTask(rs.getLong("id"), rs.getLong("work_order_id"),
                rs.getLong("route_step_id"), rs.getLong("operation_id"),
                nullableLong(rs, "station_id"), rs.getInt("sequence_no"),
                rs.getBigDecimal("planned_quantity"), rs.getBigDecimal("good_quantity"),
                rs.getBigDecimal("bad_quantity"), TaskStatus.valueOf(rs.getString("status")),
                rs.getLong("version"));
    }

    private static TraceEvent mapTrace(ResultSet rs) throws SQLException {
        return new TraceEvent(rs.getLong("id"), rs.getLong("work_order_id"),
                nullableLong(rs, "task_id"), rs.getString("batch_no"), rs.getString("barcode"),
                rs.getString("event_type"), rs.getBigDecimal("good_quantity"),
                rs.getBigDecimal("bad_quantity"), rs.getTimestamp("occurred_at").toInstant());
    }

    private static AlarmAction mapAlarm(ResultSet rs) throws SQLException {
        return new AlarmAction(rs.getLong("id"), rs.getLong("source_alarm_id"),
                AlarmActionStatus.valueOf(rs.getString("status")), rs.getString("assignee"),
                rs.getString("resolution"), rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private long insertKey(String sql, Object... arguments) {
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement =
                    connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            return statement;
        }, holder);
        Number key = holder.getKey();
        if (key == null) {
            throw new IllegalStateException("数据库未返回主键");
        }
        return key.longValue();
    }

    private String writeAttributes(Map<String, String> attributes) {
        try {
            return objectMapper.writeValueAsString(attributes);
        } catch (Exception exception) {
            throw new IllegalArgumentException("主数据属性无法序列化", exception);
        }
    }

    private Map<String, String> readAttributes(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, STRING_MAP);
        } catch (Exception exception) {
            throw new IllegalStateException("主数据属性无法读取", exception);
        }
    }

    private static void requireChanged(int changed) {
        if (changed != 1) {
            throw new ProductionService.OptimisticLockException();
        }
    }

    private static Long nullableLong(ResultSet rs, String name) throws SQLException {
        long value = rs.getLong(name);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, String name) throws SQLException {
        int value = rs.getInt(name);
        return rs.wasNull() ? null : value;
    }

    private static Long parseLong(String value) {
        return value == null || value.isBlank() ? null : Long.valueOf(value);
    }
}


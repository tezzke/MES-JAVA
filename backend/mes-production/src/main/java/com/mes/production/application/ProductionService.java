package com.mes.production.application;

import com.mes.core.contract.AuditRecorder;
import com.mes.core.contract.PagedResult;
import com.mes.production.contract.ProductionRepository;
import com.mes.production.domain.MasterData;
import com.mes.production.domain.ProductionModels.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/** 工单与任务执行应用服务；控制器不直接修改状态。 */
@Service
public class ProductionService {
    private final ProductionRepository repository;
    private final AuditRecorder audit;

    public ProductionService(ProductionRepository repository, AuditRecorder audit) {
        this.repository = repository;
        this.audit = audit;
    }

    public PagedResult<WorkOrder> orders(int page, int size) { return repository.findWorkOrders(page, size); }
    public WorkOrder order(long id) { return requireOrder(id); }
    public List<ProductionTask> tasks(long orderId) { requireOrder(orderId); return repository.findTasks(orderId); }
    /** 汇总工单产出数量和路线任务完成进度。 */
    public WorkOrderProgress progress(long orderId) {
        WorkOrder order = requireOrder(orderId);
        List<ProductionTask> tasks = repository.findTasks(orderId);
        int completed = (int) tasks.stream()
                .filter(task -> task.status() == TaskStatus.COMPLETED)
                .count();
        BigDecimal percent = tasks.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(completed)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(tasks.size()), 2, RoundingMode.HALF_UP);
        return new WorkOrderProgress(orderId, order.status(), order.plannedQuantity(),
                order.goodQuantity(), order.badQuantity(), completed, tasks.size(), percent);
    }
    public PagedResult<TraceEvent> trace(String batch, String barcode, int page, int size) {
        return repository.findTrace(blank(batch), blank(barcode), page, size);
    }

    @Transactional("businessTransactionManager")
    public long saveOrder(Long id, String number, long product, long route, String batch,
                          BigDecimal planned, long version, String actor, String correlationId) {
        if (number == null || number.isBlank() || batch == null || batch.isBlank()
                || planned == null || planned.signum() <= 0) {
            throw new IllegalArgumentException("工单号、批次和正数计划数量不能为空");
        }
        MasterData.Item productItem = requireMasterData(product, MasterData.Type.PRODUCT, "产品");
        MasterData.Item routeItem = requireMasterData(route, MasterData.Type.ROUTE, "工艺路线");
        if (!Long.valueOf(productItem.id()).equals(routeItem.referenceId())) {
            throw new IllegalArgumentException("工艺路线不属于所选产品");
        }
        long saved;
        if (id == null) {
            saved = repository.insertWorkOrder(new WorkOrder(null, number, product, route, batch,
                    planned, BigDecimal.ZERO, BigDecimal.ZERO, WorkOrderStatus.DRAFT, 0, Instant.now()));
        } else {
            WorkOrder old = requireOrder(id);
            if (old.status() != WorkOrderStatus.DRAFT) throw new IllegalStateException("只有草稿工单可以编辑");
            repository.updateWorkOrder(new WorkOrder(id, number, product, route, batch, planned,
                    old.goodQuantity(), old.badQuantity(), old.status(), version, old.createdAt()));
            saved = id;
        }
        audit.record(actor, id == null ? "ORDER_CREATE" : "ORDER_UPDATE",
                "WORK_ORDER", String.valueOf(saved), correlationId);
        return saved;
    }

    /** 删除尚未下达的草稿工单。 */
    @Transactional("businessTransactionManager")
    public void deleteOrder(long id, long version, String actor, String correlationId) {
        WorkOrder order = requireOrder(id);
        if (order.status() != WorkOrderStatus.DRAFT) {
            throw new IllegalStateException("只有草稿工单可以删除");
        }
        version(order.version(), version);
        repository.deleteDraftWorkOrder(id, version);
        audit.record(actor, "ORDER_DELETE", "WORK_ORDER", Long.toString(id), correlationId);
    }

    @Transactional("businessTransactionManager")
    public int release(long id, long version, String actor, String correlationId) {
        WorkOrder order = requireOrder(id);
        order.status().requireTransitionTo(WorkOrderStatus.RELEASED);
        version(order.version(), version);
        int count = repository.generateTasks(id, order.routeId(), order.plannedQuantity());
        if (count == 0) throw new IllegalStateException("工艺路线没有可用步骤");
        repository.changeWorkOrderStatus(id, version, order.status().name(), WorkOrderStatus.RELEASED.name());
        audit.record(actor, "ORDER_RELEASE", "WORK_ORDER", String.valueOf(id), correlationId);
        return count;
    }

    @Transactional("businessTransactionManager")
    public long startTask(long id, long version, String barcode, String key,
                          String actor, String correlationId) {
        Long duplicate = begin("TASK_START", key);
        if (duplicate != null) return duplicate;
        ProductionTask task = requireTask(id);
        task.status().requireTransitionTo(TaskStatus.IN_PROGRESS);
        version(task.version(), version);
        if (!repository.allPriorTasksCompleted(task.workOrderId(), task.sequence())) {
            throw new IllegalStateException("前序任务尚未完工");
        }
        repository.changeTaskStatus(id, version, task.status().name(), TaskStatus.IN_PROGRESS.name());
        WorkOrder order = requireOrder(task.workOrderId());
        if (order.status() == WorkOrderStatus.RELEASED) {
            repository.changeWorkOrderStatus(order.id(), order.version(), order.status().name(),
                    WorkOrderStatus.IN_PROGRESS.name());
        } else if (order.status() != WorkOrderStatus.IN_PROGRESS) {
            throw new IllegalStateException("工单当前状态不能开工");
        }
        trace(order, id, barcode, "TASK_STARTED", BigDecimal.ZERO, BigDecimal.ZERO);
        finish("TASK_START", key, id);
        audit.record(actor, "TASK_START", "PRODUCTION_TASK", String.valueOf(id), correlationId);
        return id;
    }

    @Transactional("businessTransactionManager")
    public long report(long id, long taskVersion, long orderVersion, BigDecimal good, BigDecimal bad,
                       String barcode, String key, String actor, String correlationId) {
        Long duplicate = begin("TASK_REPORT", key);
        if (duplicate != null) return duplicate;
        ProductionTask task = requireTask(id);
        WorkOrder order = requireOrder(task.workOrderId());
        version(task.version(), taskVersion);
        version(order.version(), orderVersion);
        task.validateReport(good, bad);
        boolean finalTask = repository.isFinalTask(task.workOrderId(), task.sequence());
        if (finalTask) {
            order.validateReport(good, bad);
        }
        repository.addReport(id, taskVersion, order.id(), orderVersion, good, bad, finalTask);
        trace(order, id, barcode, "QUANTITY_REPORTED", good, bad);
        finish("TASK_REPORT", key, id);
        audit.record(actor, "TASK_REPORT", "PRODUCTION_TASK", String.valueOf(id), correlationId);
        return id;
    }

    @Transactional("businessTransactionManager")
    public long completeTask(long id, long version, String barcode, String key,
                             String actor, String correlationId) {
        Long duplicate = begin("TASK_COMPLETE", key);
        if (duplicate != null) return duplicate;
        ProductionTask task = requireTask(id);
        task.status().requireTransitionTo(TaskStatus.COMPLETED);
        version(task.version(), version);
        if (task.goodQuantity().add(task.badQuantity()).compareTo(task.plannedQuantity()) != 0) {
            throw new IllegalStateException("任务报工达到计划数量后才能完工");
        }
        repository.changeTaskStatus(id, version, task.status().name(), TaskStatus.COMPLETED.name());
        WorkOrder order = requireOrder(task.workOrderId());
        if (repository.allTasksCompleted(order.id())) {
            order.status().requireTransitionTo(WorkOrderStatus.COMPLETED);
            repository.changeWorkOrderStatus(order.id(), order.version(), order.status().name(),
                    WorkOrderStatus.COMPLETED.name());
        }
        trace(order, id, barcode, "TASK_COMPLETED", BigDecimal.ZERO, BigDecimal.ZERO);
        finish("TASK_COMPLETE", key, id);
        audit.record(actor, "TASK_COMPLETE", "PRODUCTION_TASK", String.valueOf(id), correlationId);
        return id;
    }

    private Long begin(String operation, String key) {
        if (key == null || key.isBlank() || key.length() > 100) throw new IllegalArgumentException("幂等键不合法");
        Long done = repository.findIdempotentResult(operation, key).orElse(null);
        if (done != null) return done;
        if (!repository.claimIdempotency(operation, key)) {
            return repository.findIdempotentResult(operation, key)
                    .orElseThrow(() -> new IllegalStateException("幂等请求正在处理"));
        }
        return null;
    }

    private void finish(String operation, String key, long id) {
        repository.completeIdempotency(operation, key, id);
    }

    private void trace(WorkOrder order, long task, String barcode, String type,
                       BigDecimal good, BigDecimal bad) {
        if (barcode == null || barcode.isBlank() || barcode.length() > 200)
            throw new IllegalArgumentException("条码不合法");
        repository.appendTrace(new TraceEvent(null, order.id(), task, order.batchNo(), barcode,
                type, good, bad, Instant.now()));
    }

    private WorkOrder requireOrder(long id) {
        return repository.findWorkOrder(id).orElseThrow(() -> new IllegalArgumentException("工单不存在"));
    }
    private ProductionTask requireTask(long id) {
        return repository.findTask(id).orElseThrow(() -> new IllegalArgumentException("任务不存在"));
    }
    private MasterData.Item requireMasterData(long id, MasterData.Type type, String label) {
        MasterData.Item item = repository.findMasterData(id)
                .orElseThrow(() -> new IllegalArgumentException(label + "不存在"));
        if (item.type() != type || !item.enabled()) {
            throw new IllegalArgumentException(label + "类型不正确或已禁用");
        }
        return item;
    }
    private static void version(long actual, long expected) {
        if (actual != expected) throw new OptimisticLockException();
    }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value; }

    /** 乐观锁冲突。 */
    public static final class OptimisticLockException extends RuntimeException {
        public OptimisticLockException() { super("数据已被其他请求修改，请刷新后重试"); }
    }
}

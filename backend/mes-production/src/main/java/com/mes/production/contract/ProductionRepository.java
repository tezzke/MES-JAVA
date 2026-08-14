package com.mes.production.contract;

import com.mes.core.contract.PagedResult;
import com.mes.production.domain.MasterData;
import com.mes.production.domain.ProductionModels.AlarmAction;
import com.mes.production.domain.ProductionModels.ProductionTask;
import com.mes.production.domain.ProductionModels.TraceEvent;
import com.mes.production.domain.ProductionModels.WorkOrder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** 生产域持久化端口。 */
public interface ProductionRepository {
    PagedResult<MasterData.Item> findMasterData(MasterData.Type type, int page, int size);
    Optional<MasterData.Item> findMasterData(long id);
    long saveMasterData(MasterData.Item item);
    PagedResult<WorkOrder> findWorkOrders(int page, int size);
    Optional<WorkOrder> findWorkOrder(long id);
    long insertWorkOrder(WorkOrder order);
    void updateWorkOrder(WorkOrder order);
    void deleteDraftWorkOrder(long id, long version);
    void changeWorkOrderStatus(long id, long version, String fromStatus, String toStatus);
    int generateTasks(long workOrderId, long routeId, BigDecimal plannedQuantity);
    List<ProductionTask> findTasks(long workOrderId);
    Optional<ProductionTask> findTask(long taskId);
    boolean allPriorTasksCompleted(long workOrderId, int sequence);
    boolean isFinalTask(long workOrderId, int sequence);
    void changeTaskStatus(long taskId, long version, String fromStatus, String toStatus);
    void addReport(long taskId, long taskVersion, long workOrderId, long orderVersion,
                   BigDecimal good, BigDecimal bad, boolean updateWorkOrder);
    boolean allTasksCompleted(long workOrderId);
    void appendTrace(TraceEvent event);
    PagedResult<TraceEvent> findTrace(String batchNo, String barcode, int page, int size);
    Optional<Long> findIdempotentResult(String operation, String key);
    boolean claimIdempotency(String operation, String key);
    void completeIdempotency(String operation, String key, long resultId);
    PagedResult<AlarmAction> findAlarmActions(int page, int size);
    Optional<AlarmAction> findAlarmAction(long id);
    Optional<AlarmAction> findAlarmActionBySource(long sourceAlarmId);
    long insertAlarmAction(long sourceAlarmId);
    void updateAlarmAction(AlarmAction current, String targetStatus,
                           String assignee, String resolution);
}

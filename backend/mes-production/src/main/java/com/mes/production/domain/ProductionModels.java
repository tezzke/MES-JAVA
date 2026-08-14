package com.mes.production.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 工单、任务、追溯和报警处置领域模型。
 */
public final class ProductionModels {

    private ProductionModels() {
    }

    /** 工单状态。 */
    public enum WorkOrderStatus {
        DRAFT, RELEASED, IN_PROGRESS, COMPLETED, CANCELLED;

        /** 校验工单状态转换。 */
        public void requireTransitionTo(WorkOrderStatus target) {
            boolean valid = switch (this) {
                case DRAFT -> target == RELEASED || target == CANCELLED;
                case RELEASED -> target == IN_PROGRESS || target == CANCELLED;
                case IN_PROGRESS -> target == COMPLETED || target == CANCELLED;
                case COMPLETED, CANCELLED -> false;
            };
            if (!valid) {
                throw new IllegalStateException("工单状态不允许从 " + this + " 转为 " + target);
            }
        }
    }

    /** 生产任务状态。 */
    public enum TaskStatus {
        READY, IN_PROGRESS, COMPLETED;

        /** 校验任务状态转换。 */
        public void requireTransitionTo(TaskStatus target) {
            if (!((this == READY && target == IN_PROGRESS)
                    || (this == IN_PROGRESS && target == COMPLETED))) {
                throw new IllegalStateException("任务状态不允许从 " + this + " 转为 " + target);
            }
        }
    }

    /** 报警处置状态。 */
    public enum AlarmActionStatus {
        NEW, ACKNOWLEDGED, ASSIGNED, RESOLVED, CLOSED;

        /** 校验报警处置状态转换。 */
        public void requireTransitionTo(AlarmActionStatus target) {
            boolean valid = switch (this) {
                case NEW -> target == ACKNOWLEDGED;
                case ACKNOWLEDGED -> target == ASSIGNED || target == RESOLVED;
                case ASSIGNED -> target == RESOLVED;
                case RESOLVED -> target == CLOSED;
                case CLOSED -> false;
            };
            if (!valid) {
                throw new IllegalStateException("报警处置状态不允许从 " + this + " 转为 " + target);
            }
        }
    }

    /** 工单。 */
    public record WorkOrder(Long id, String orderNo, long productId, long routeId,
                            String batchNo, BigDecimal plannedQuantity,
                            BigDecimal goodQuantity, BigDecimal badQuantity,
                            WorkOrderStatus status, long version, Instant createdAt) {
        /** 校验累计报工数量。 */
        public void validateReport(BigDecimal good, BigDecimal bad) {
            if (good == null || bad == null || good.signum() < 0 || bad.signum() < 0
                    || good.add(bad).signum() == 0) {
                throw new IllegalArgumentException("合格与不良数量必须非负且合计大于零");
            }
            if (goodQuantity.add(badQuantity).add(good).add(bad)
                    .compareTo(plannedQuantity) > 0) {
                throw new IllegalArgumentException("累计报工数量不能超过计划数量");
            }
        }
    }

    /** 路线生成的生产任务。 */
    public record ProductionTask(Long id, long workOrderId, long routeStepId,
                                 long operationId, Long stationId, int sequence,
                                 BigDecimal plannedQuantity, BigDecimal goodQuantity,
                                 BigDecimal badQuantity, TaskStatus status, long version) {
        /** 校验本任务报工数量。 */
        public void validateReport(BigDecimal good, BigDecimal bad) {
            if (status != TaskStatus.IN_PROGRESS) {
                throw new IllegalStateException("只有进行中的任务可以报工");
            }
            if (good == null || bad == null || good.signum() < 0 || bad.signum() < 0
                    || good.add(bad).signum() == 0) {
                throw new IllegalArgumentException("报工数量必须非负且合计大于零");
            }
            if (goodQuantity.add(badQuantity).add(good).add(bad)
                    .compareTo(plannedQuantity) > 0) {
                throw new IllegalArgumentException("任务累计报工数量不能超过计划数量");
            }
        }
    }

    /** 工单数量与任务完成进度。 */
    public record WorkOrderProgress(long workOrderId, WorkOrderStatus status,
                                    BigDecimal plannedQuantity, BigDecimal goodQuantity,
                                    BigDecimal badQuantity, int completedTasks, int totalTasks,
                                    BigDecimal completionPercent) {
    }

    /** 批次或条码追溯事件。 */
    public record TraceEvent(Long id, long workOrderId, Long taskId, String batchNo,
                             String barcode, String eventType, BigDecimal goodQuantity,
                             BigDecimal badQuantity, Instant occurredAt) {
    }

    /** MySQL 中的报警处置聚合，不包含 SQLite 报警详情。 */
    public record AlarmAction(Long id, long sourceAlarmId, AlarmActionStatus status,
                              String assignee, String resolution, long version,
                              Instant createdAt, Instant updatedAt) {
    }
}

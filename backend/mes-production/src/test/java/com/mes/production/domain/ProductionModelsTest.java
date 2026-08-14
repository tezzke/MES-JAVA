package com.mes.production.domain;

import com.mes.production.domain.ProductionModels.AlarmActionStatus;
import com.mes.production.domain.ProductionModels.ProductionTask;
import com.mes.production.domain.ProductionModels.TaskStatus;
import com.mes.production.domain.ProductionModels.WorkOrder;
import com.mes.production.domain.ProductionModels.WorkOrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionModelsTest {

    @Test
    void rejectsIllegalWorkOrderAndTaskTransitions() {
        assertThrows(IllegalStateException.class,
                () -> WorkOrderStatus.DRAFT.requireTransitionTo(WorkOrderStatus.COMPLETED));
        assertThrows(IllegalStateException.class,
                () -> TaskStatus.READY.requireTransitionTo(TaskStatus.COMPLETED));
        assertDoesNotThrow(() -> WorkOrderStatus.DRAFT.requireTransitionTo(WorkOrderStatus.RELEASED));
    }

    @Test
    void enforcesTaskAndOrderQuantityBoundaries() {
        ProductionTask task = new ProductionTask(1L, 2L, 3L, 4L, null, 1,
                new BigDecimal("10"), new BigDecimal("8"), BigDecimal.ONE,
                TaskStatus.IN_PROGRESS, 0);
        assertDoesNotThrow(() -> task.validateReport(BigDecimal.ONE, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> task.validateReport(new BigDecimal("1.01"), BigDecimal.ZERO));

        WorkOrder order = new WorkOrder(2L, "WO-1", 1, 1, "B-1",
                new BigDecimal("10"), new BigDecimal("9"), BigDecimal.ZERO,
                WorkOrderStatus.IN_PROGRESS, 0, Instant.now());
        assertThrows(IllegalArgumentException.class,
                () -> order.validateReport(BigDecimal.ONE, BigDecimal.ONE));
    }

    @Test
    void enforcesAlarmActionStateMachine() {
        assertDoesNotThrow(() -> AlarmActionStatus.NEW
                .requireTransitionTo(AlarmActionStatus.ACKNOWLEDGED));
        assertThrows(IllegalStateException.class,
                () -> AlarmActionStatus.NEW.requireTransitionTo(AlarmActionStatus.CLOSED));
        assertThrows(IllegalStateException.class,
                () -> AlarmActionStatus.CLOSED.requireTransitionTo(AlarmActionStatus.ASSIGNED));
    }
}

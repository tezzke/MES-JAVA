package com.mes.production.application;

import com.mes.core.contract.AuditRecorder;
import com.mes.production.contract.ProductionRepository;
import com.mes.production.domain.ProductionModels.AlarmAction;
import com.mes.production.domain.ProductionModels.AlarmActionStatus;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 报警确认、指派、处理和关闭流程测试。 */
class AlarmActionServiceTest {

    @Test
    void completesAlarmActionFlowWithOptimisticVersions() {
        ProductionRepository repository = mock(ProductionRepository.class);
        AuditRecorder audit = mock(AuditRecorder.class);
        AlarmActionService service = new AlarmActionService(repository, audit);
        long id = 41L;
        when(repository.findAlarmActionBySource(9001L)).thenReturn(Optional.empty());
        when(repository.insertAlarmAction(9001L)).thenReturn(id);
        when(repository.findAlarmAction(id)).thenReturn(
                Optional.of(action(id, AlarmActionStatus.NEW, 0, null, null)),
                Optional.of(action(id, AlarmActionStatus.ACKNOWLEDGED, 1, null, null)),
                Optional.of(action(id, AlarmActionStatus.ASSIGNED, 2, "张工", null)),
                Optional.of(action(id, AlarmActionStatus.RESOLVED, 3, "张工", "已复位")));

        assertEquals(id, service.acknowledge(9001L, "operator", "corr-1"));
        service.assign(id, 1, "张工", "operator", "corr-2");
        service.resolve(id, 2, "已复位", "operator", "corr-3");
        service.close(id, 3, "operator", "corr-4");

        InOrder transitions = inOrder(repository);
        transitions.verify(repository).updateAlarmAction(
                argThat(value -> value.status() == AlarmActionStatus.NEW),
                eq("ACKNOWLEDGED"), isNull(), isNull());
        transitions.verify(repository).updateAlarmAction(
                argThat(value -> value.status() == AlarmActionStatus.ACKNOWLEDGED),
                eq("ASSIGNED"), eq("张工"), isNull());
        transitions.verify(repository).updateAlarmAction(
                argThat(value -> value.status() == AlarmActionStatus.ASSIGNED),
                eq("RESOLVED"), eq("张工"), eq("已复位"));
        transitions.verify(repository).updateAlarmAction(
                argThat(value -> value.status() == AlarmActionStatus.RESOLVED),
                eq("CLOSED"), eq("张工"), eq("已复位"));
        verify(audit, times(4)).record(eq("operator"), anyString(),
                eq("ALARM_ACTION"), eq(Long.toString(id)), anyString());
    }

    @Test
    void rejectsStaleAlarmActionVersion() {
        ProductionRepository repository = mock(ProductionRepository.class);
        AlarmActionService service = new AlarmActionService(repository, mock(AuditRecorder.class));
        when(repository.findAlarmAction(8L)).thenReturn(Optional.of(
                action(8, AlarmActionStatus.ACKNOWLEDGED, 2, null, null)));

        assertThrows(ProductionService.OptimisticLockException.class,
                () -> service.assign(8, 1, "李工", "operator", "corr"));
        verify(repository, never()).updateAlarmAction(any(), anyString(), any(), any());
    }

    private static AlarmAction action(long id, AlarmActionStatus status, long version,
                                      String assignee, String resolution) {
        Instant now = Instant.parse("2026-08-14T00:00:00Z");
        return new AlarmAction(id, 9001, status, assignee, resolution, version, now, now);
    }
}

package com.mes.production.application;

import com.mes.core.contract.AuditRecorder;
import com.mes.core.contract.PagedResult;
import com.mes.production.contract.ProductionRepository;
import com.mes.production.domain.ProductionModels.AlarmAction;
import com.mes.production.domain.ProductionModels.AlarmActionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 报警处置应用服务；只保存源报警 ID，不修改 SQLite 报警。 */
@Service
public class AlarmActionService {
    private final ProductionRepository repository;
    private final AuditRecorder auditRecorder;

    public AlarmActionService(ProductionRepository repository, AuditRecorder auditRecorder) {
        this.repository = repository;
        this.auditRecorder = auditRecorder;
    }

    /** 分页查询报警处置单。 */
    public PagedResult<AlarmAction> list(int page, int size) {
        return repository.findAlarmActions(page, size);
    }

    /** 查询报警处置详情。 */
    public AlarmAction detail(long id) {
        return repository.findAlarmAction(id)
                .orElseThrow(() -> new IllegalArgumentException("报警处置不存在"));
    }

    /** 幂等确认源报警。 */
    @Transactional("businessTransactionManager")
    public long acknowledge(long sourceAlarmId, String actor, String correlationId) {
        AlarmAction action = repository.findAlarmActionBySource(sourceAlarmId).orElse(null);
        long id = action == null ? repository.insertAlarmAction(sourceAlarmId) : action.id();
        action = detail(id);
        if (action.status() == AlarmActionStatus.NEW) {
            transition(action, AlarmActionStatus.ACKNOWLEDGED, null, null);
            auditRecorder.record(actor, "ALARM_ACKNOWLEDGE", "ALARM_ACTION",
                    Long.toString(id), correlationId);
        } else if (action.status() != AlarmActionStatus.ACKNOWLEDGED) {
            throw new IllegalStateException("报警已进入后续处置状态");
        }
        return id;
    }

    /** 指派报警处置责任人。 */
    @Transactional("businessTransactionManager")
    public void assign(long id, long version, String assignee, String actor, String correlationId) {
        if (assignee == null || assignee.isBlank()) {
            throw new IllegalArgumentException("指派人不能为空");
        }
        AlarmAction action = requireVersion(id, version);
        transition(action, AlarmActionStatus.ASSIGNED, assignee, action.resolution());
        auditRecorder.record(actor, "ALARM_ASSIGN", "ALARM_ACTION", Long.toString(id), correlationId);
    }

    /** 记录报警处理结果。 */
    @Transactional("businessTransactionManager")
    public void resolve(long id, long version, String resolution, String actor, String correlationId) {
        if (resolution == null || resolution.isBlank()) {
            throw new IllegalArgumentException("处理说明不能为空");
        }
        AlarmAction action = requireVersion(id, version);
        transition(action, AlarmActionStatus.RESOLVED, action.assignee(), resolution);
        auditRecorder.record(actor, "ALARM_RESOLVE", "ALARM_ACTION", Long.toString(id), correlationId);
    }

    /** 关闭已处理报警。 */
    @Transactional("businessTransactionManager")
    public void close(long id, long version, String actor, String correlationId) {
        AlarmAction action = requireVersion(id, version);
        transition(action, AlarmActionStatus.CLOSED, action.assignee(), action.resolution());
        auditRecorder.record(actor, "ALARM_CLOSE", "ALARM_ACTION", Long.toString(id), correlationId);
    }

    private AlarmAction requireVersion(long id, long version) {
        AlarmAction action = detail(id);
        if (action.version() != version) {
            throw new ProductionService.OptimisticLockException();
        }
        return action;
    }

    private void transition(AlarmAction action, AlarmActionStatus target,
                            String assignee, String resolution) {
        action.status().requireTransitionTo(target);
        repository.updateAlarmAction(action, target.name(), assignee, resolution);
    }
}


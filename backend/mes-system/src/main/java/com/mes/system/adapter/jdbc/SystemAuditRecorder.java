package com.mes.system.adapter.jdbc;

import com.mes.core.contract.AuditRecorder;
import com.mes.system.contract.SystemRepository;
import com.mes.system.domain.AuditEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * 使用系统审计表实现跨模块审计端口。
 */
@Component
public class SystemAuditRecorder implements AuditRecorder {

    private final SystemRepository repository;
    private final Clock clock;

    @Autowired
    public SystemAuditRecorder(SystemRepository repository) {
        this(repository, Clock.systemUTC());
    }

    SystemAuditRecorder(SystemRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public void record(String actor, String action, String targetType, String targetId,
                       String correlationId) {
        repository.appendAudit(new AuditEvent(null, actor, action, targetType, targetId,
                "SUCCESS", correlationId, null, clock.instant()));
    }
}

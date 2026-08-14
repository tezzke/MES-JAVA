package com.mes.system.domain;

import java.time.Instant;

/** 只追加、不更新的安全审计事件。 */
public record AuditEvent(Long id, String actor, String action, String targetType,
                         String targetId, String result, String correlationId,
                         String clientIp, Instant occurredAt) {
}

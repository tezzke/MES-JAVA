package com.mes.core.contract;

/**
 * 跨业务域的最小审计端口。
 * 调用方只传操作人与目标标识，不应传密码、完整条码等敏感业务内容。
 */
public interface AuditRecorder {

    /**
     * 记录一次关键写操作。
     *
     * @param actor 操作人
     * @param action 动作码
     * @param targetType 目标类型
     * @param targetId 目标主键
     * @param correlationId 请求关联标识
     */
    void record(String actor, String action, String targetType, String targetId, String correlationId);
}

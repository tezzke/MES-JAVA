package com.mes.production.application;

import com.mes.core.contract.AuditRecorder;
import com.mes.core.contract.PagedResult;
import com.mes.production.contract.ProductionRepository;
import com.mes.production.domain.MasterData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

/** 生产主数据应用服务，统一执行业务校验、事务和审计。 */
@Service
public class MasterDataService {
    private final ProductionRepository repository;
    private final AuditRecorder auditRecorder;

    public MasterDataService(ProductionRepository repository, AuditRecorder auditRecorder) {
        this.repository = repository;
        this.auditRecorder = auditRecorder;
    }

    /** 分页查询指定类型主数据。 */
    public PagedResult<MasterData.Item> list(MasterData.Type type, int page, int size) {
        return repository.findMasterData(type, page, size);
    }

    /** 查询主数据详情。 */
    public MasterData.Item detail(long id) {
        return repository.findMasterData(id)
                .orElseThrow(() -> new IllegalArgumentException("主数据不存在"));
    }

    /** 新增或按版本更新主数据。 */
    @Transactional("businessTransactionManager")
    public long save(MasterData.Item item, String actor, String correlationId) {
        validate(item);
        long id = repository.saveMasterData(item);
        auditRecorder.record(actor, item.id() == null ? "MASTER_CREATE" : "MASTER_UPDATE",
                item.type().name(), Long.toString(id), correlationId);
        return id;
    }

    private void validate(MasterData.Item item) {
        if (item.type() == null || item.code() == null || item.code().isBlank()
                || item.name() == null || item.name().isBlank()) {
            throw new IllegalArgumentException("类型、编码和名称不能为空");
        }
        if (EnumSet.of(MasterData.Type.BOM, MasterData.Type.BOM_ITEM, MasterData.Type.LINE,
                MasterData.Type.STATION, MasterData.Type.EQUIPMENT_BINDING, MasterData.Type.ROUTE,
                MasterData.Type.ROUTE_STEP).contains(item.type()) && item.referenceId() == null) {
            throw new IllegalArgumentException("该类型必须提供主引用");
        }
        if (EnumSet.of(MasterData.Type.BOM_ITEM, MasterData.Type.ROUTE_STEP).contains(item.type())
                && item.secondaryReferenceId() == null) {
            throw new IllegalArgumentException("该类型必须提供次引用");
        }
        if (item.type() == MasterData.Type.EQUIPMENT_BINDING
                && (item.attributes().get("deviceId") == null
                || item.attributes().get("deviceId").isBlank())) {
            throw new IllegalArgumentException("设备绑定必须在属性中提供 devices.json 的 deviceId");
        }
        if (item.type() == MasterData.Type.BOM_ITEM
                && (item.quantity() == null || item.quantity().signum() <= 0)) {
            throw new IllegalArgumentException("BOM 用量必须大于零");
        }
        if (EnumSet.of(MasterData.Type.BOM_ITEM, MasterData.Type.ROUTE_STEP).contains(item.type())
                && (item.sequence() == null || item.sequence() <= 0)) {
            throw new IllegalArgumentException("顺序必须大于零");
        }
        switch (item.type()) {
            case BOM -> requireReference(item.referenceId(), MasterData.Type.PRODUCT, "BOM 产品");
            case BOM_ITEM -> {
                requireReference(item.referenceId(), MasterData.Type.BOM, "BOM");
                requireReference(item.secondaryReferenceId(), MasterData.Type.MATERIAL, "BOM 物料");
            }
            case LINE -> requireReference(item.referenceId(), MasterData.Type.WORKSHOP, "车间");
            case STATION -> requireReference(item.referenceId(), MasterData.Type.LINE, "产线");
            case EQUIPMENT_BINDING ->
                    requireReference(item.referenceId(), MasterData.Type.STATION, "工位");
            case ROUTE -> requireReference(item.referenceId(), MasterData.Type.PRODUCT, "工艺路线产品");
            case ROUTE_STEP -> {
                requireReference(item.referenceId(), MasterData.Type.ROUTE, "工艺路线");
                requireReference(item.secondaryReferenceId(), MasterData.Type.OPERATION, "工序");
                String stationId = item.attributes().get("stationId");
                if (stationId != null && !stationId.isBlank()) {
                    try {
                        requireReference(Long.valueOf(stationId), MasterData.Type.STATION, "工位");
                    } catch (NumberFormatException exception) {
                        throw new IllegalArgumentException("工位 ID 必须是数字", exception);
                    }
                }
            }
            default -> {
                // MATERIAL、PRODUCT、WORKSHOP、OPERATION 没有上级引用。
            }
        }
    }

    private void requireReference(Long id, MasterData.Type expectedType, String label) {
        MasterData.Item referenced = repository.findMasterData(id == null ? -1 : id)
                .orElseThrow(() -> new IllegalArgumentException(label + "不存在"));
        if (referenced.type() != expectedType || !referenced.enabled()) {
            throw new IllegalArgumentException(label + "类型不正确或已禁用");
        }
    }
}


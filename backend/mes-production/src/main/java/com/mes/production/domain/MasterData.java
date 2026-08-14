package com.mes.production.domain;

import java.math.BigDecimal;
import java.util.Map;

/** 首期生产主数据领域类型。 */
public final class MasterData {
    private MasterData() { }

    /** 明确的主数据类别。 */
    public enum Type {
        MATERIAL, PRODUCT, BOM, BOM_ITEM, WORKSHOP, LINE, STATION,
        EQUIPMENT_BINDING, OPERATION, ROUTE, ROUTE_STEP
    }

    /** 统一维护视图；引用字段分别表达父级/主体和物料/设备/工序。 */
    public record Item(Long id, Type type, String code, String name, Long referenceId,
                       Long secondaryReferenceId, Integer sequence, BigDecimal quantity,
                       Map<String, String> attributes, boolean enabled, long version) {
        public Item {
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }

    public record Material(Long id, String code, String name, boolean product, String unit, long version) { }
    public record Bom(Long id, String code, long productId, long version) { }
    public record BomItem(Long id, long bomId, long materialId, BigDecimal quantity, int sequence, long version) { }
    public record SiteNode(Long id, Type type, String code, String name, Long parentId, long version) { }
    /** 工位与 devices.json 字符串设备标识的绑定。 */
    public record EquipmentBinding(Long id, long stationId, String deviceId, long version) { }
    public record Operation(Long id, String code, String name, long version) { }
    public record Route(Long id, String code, long productId, long version) { }
    public record RouteStep(Long id, long routeId, long operationId, Long stationId, int sequence, long version) { }
}

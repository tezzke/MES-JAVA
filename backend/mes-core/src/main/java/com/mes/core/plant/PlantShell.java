package com.mes.core.plant;

import java.util.ArrayList;
import java.util.List;

/**
 * 厂房壳体:外墙包围盒 + 轴网 + 隔墙 + 分区。
 * <p>
 * 所有坐标单位为米,取自建筑图纸并按同一个原点换算(见 {@link #origin} 说明),
 * 因此 3D 场景中量出来的距离与图纸上量出来的一致,可直接对图核验。
 */
public class PlantShell {

    /** 厂房名称,显示在 3D 场景标题与图例上。 */
    private String name = "";

    /** 图纸编号,便于回溯到具体那张施工图。 */
    private String drawingNo = "";

    /** 数据来源说明(图纸文件名、设计单位、出图日期、比例)。 */
    private String source = "";

    /** 坐标原点与轴向的文字说明,现场核验坐标时必读。 */
    private String origin = "";

    /** 外墙高度(米)。 */
    private double wallHeight = 6.0;

    /** 外墙厚度(米)。 */
    private double wallThickness = 0.25;

    /** 隔墙默认高度(米),单段隔墙可用 {@link WallSegment#getHeight()} 覆盖。 */
    private double partitionHeight = 4.0;

    /** 外墙包围盒。 */
    private Bounds envelope = new Bounds();

    /** 轴网。 */
    private AxisGrid axisGrid = new AxisGrid();

    /** 内部隔墙。 */
    private List<WallSegment> partitions = new ArrayList<>();

    /** 功能分区(用于地面着色与区域标牌)。 */
    private List<PlantZone> zones = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDrawingNo() {
        return drawingNo;
    }

    public void setDrawingNo(String drawingNo) {
        this.drawingNo = drawingNo;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public double getWallHeight() {
        return wallHeight;
    }

    public void setWallHeight(double wallHeight) {
        this.wallHeight = wallHeight;
    }

    public double getWallThickness() {
        return wallThickness;
    }

    public void setWallThickness(double wallThickness) {
        this.wallThickness = wallThickness;
    }

    public double getPartitionHeight() {
        return partitionHeight;
    }

    public void setPartitionHeight(double partitionHeight) {
        this.partitionHeight = partitionHeight;
    }

    public Bounds getEnvelope() {
        return envelope;
    }

    public void setEnvelope(Bounds envelope) {
        this.envelope = envelope;
    }

    public AxisGrid getAxisGrid() {
        return axisGrid;
    }

    public void setAxisGrid(AxisGrid axisGrid) {
        this.axisGrid = axisGrid;
    }

    public List<WallSegment> getPartitions() {
        return partitions;
    }

    public void setPartitions(List<WallSegment> partitions) {
        this.partitions = partitions;
    }

    public List<PlantZone> getZones() {
        return zones;
    }

    public void setZones(List<PlantZone> zones) {
        this.zones = zones;
    }
}

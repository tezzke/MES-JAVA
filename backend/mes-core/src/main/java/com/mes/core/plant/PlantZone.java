package com.mes.core.plant;

/**
 * 功能分区:一块矩形区域 + 名称与用途。
 * 3D 场景用它给地面着色并立区域标牌,让人一眼看出"这片是涂布区、那条是参观通道"。
 */
public class PlantZone {

    /** 分区名称,如 "涂布区"、"参观通道"。 */
    private String name = "";

    /**
     * 分区用途,决定地面配色:
     * clean(洁净生产区) / corridor(参观通道) / utility(公辅设备区) / office(办公辅助区)。
     */
    private String kind = "clean";

    /** 分区范围。 */
    private Bounds bounds = new Bounds();

    /** 该区对应的工艺段名称,与 DeviceConfig.line 对齐,便于点击分区筛设备。 */
    private String line = "";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public Bounds getBounds() {
        return bounds;
    }

    public void setBounds(Bounds bounds) {
        this.bounds = bounds;
    }

    public String getLine() {
        return line;
    }

    public void setLine(String line) {
        this.line = line;
    }
}

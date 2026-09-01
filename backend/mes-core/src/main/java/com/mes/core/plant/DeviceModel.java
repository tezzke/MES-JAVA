package com.mes.core.plant;

import java.util.ArrayList;
import java.util.List;

/**
 * 一类设备的外观模型:占地尺寸 + 主色 + 若干构件。
 * <p>
 * 尺寸与颜色全部来自设备技术协议书(签字件),{@link #source} 记录出处页码,
 * 现场验收时可以按图核对"3D 里这台机器的高度是不是协议里写的 1972mm"。
 * <p>
 * 构件列表是一个极小的建模语言:前端按 {@link ModelPart#getKind()} 逐个生成几何体,
 * 因此新增/调整设备外观只改本配置,不动前端代码。
 */
public class DeviceModel {

    /** 设备名称(协议书原文名称)。 */
    private String name = "";

    /** 型号。 */
    private String model = "";

    /** 制造厂家。 */
    private String vendor = "";

    /** 数据出处,如 "10、入壳机 协议书 p2/p6"。 */
    private String source = "";

    /** 占地长度(米),沿设备局部 X 轴。 */
    private double length = 2.0;

    /** 占地宽度(米),沿设备局部 Z 轴。 */
    private double width = 2.0;

    /** 整机高度(米)。 */
    private double height = 2.0;

    /** 主体颜色(#RRGGBB),构件未指定颜色时继承此色。 */
    private String color = "#c9ced6";

    /**
     * 图纸实测占位框(米),来自压缩空气平面图的设备轮廓。
     * 与 {@link #length}/{@link #width}(协议书机身尺寸)可能不同 —— 图纸框含安全围栏与操作空间,
     * 因此 3D 里用它画地面工位框,用机身尺寸画机器本体。
     */
    private double footprintLength;

    /** 图纸实测占位框宽度(米)。 */
    private double footprintWidth;

    /** 构件列表。 */
    private List<ModelPart> parts = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public double getLength() {
        return length;
    }

    public void setLength(double length) {
        this.length = length;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public double getFootprintLength() {
        return footprintLength;
    }

    public void setFootprintLength(double footprintLength) {
        this.footprintLength = footprintLength;
    }

    public double getFootprintWidth() {
        return footprintWidth;
    }

    public void setFootprintWidth(double footprintWidth) {
        this.footprintWidth = footprintWidth;
    }

    public List<ModelPart> getParts() {
        return parts;
    }

    public void setParts(List<ModelPart> parts) {
        this.parts = parts;
    }
}

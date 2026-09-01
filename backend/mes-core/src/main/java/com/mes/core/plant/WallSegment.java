package com.mes.core.plant;

/**
 * 一段隔墙:两个端点(米) + 厚度与高度。
 * 只支持直线段 —— 图纸上的隔墙都是直墙,不引入曲墙以免把配置搞复杂。
 */
public class WallSegment {

    /** 隔墙名称,仅用于配置可读性与调试。 */
    private String name = "";

    private double x1;
    private double z1;
    private double x2;
    private double z2;

    /** 墙厚(米),0 表示沿用厂房默认外墙厚度。 */
    private double thickness;

    /** 墙高(米),0 表示沿用 {@link PlantShell#getPartitionHeight()}。 */
    private double height;

    /** 是否为玻璃隔断(参观通道两侧的观察窗按玻璃渲染)。 */
    private boolean glazed;

    /** 段长(米)。 */
    public double length() {
        double dx = x2 - x1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getX1() {
        return x1;
    }

    public void setX1(double x1) {
        this.x1 = x1;
    }

    public double getZ1() {
        return z1;
    }

    public void setZ1(double z1) {
        this.z1 = z1;
    }

    public double getX2() {
        return x2;
    }

    public void setX2(double x2) {
        this.x2 = x2;
    }

    public double getZ2() {
        return z2;
    }

    public void setZ2(double z2) {
        this.z2 = z2;
    }

    public double getThickness() {
        return thickness;
    }

    public void setThickness(double thickness) {
        this.thickness = thickness;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public boolean isGlazed() {
        return glazed;
    }

    public void setGlazed(boolean glazed) {
        this.glazed = glazed;
    }
}

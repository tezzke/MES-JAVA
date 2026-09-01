package com.mes.core.plant;

/**
 * XZ 平面上的矩形范围(米),用于厂房外轮廓与功能分区。
 * Y 轴是高度方向,不参与平面范围。
 */
public class Bounds {

    private double minX;
    private double maxX;
    private double minZ;
    private double maxZ;

    /** 东西向跨度(米)。 */
    public double width() {
        return maxX - minX;
    }

    /** 南北向跨度(米)。 */
    public double depth() {
        return maxZ - minZ;
    }

    /** 中心 X 坐标(米)。 */
    public double centerX() {
        return (minX + maxX) / 2;
    }

    /** 中心 Z 坐标(米)。 */
    public double centerZ() {
        return (minZ + maxZ) / 2;
    }

    public double getMinX() {
        return minX;
    }

    public void setMinX(double minX) {
        this.minX = minX;
    }

    public double getMaxX() {
        return maxX;
    }

    public void setMaxX(double maxX) {
        this.maxX = maxX;
    }

    public double getMinZ() {
        return minZ;
    }

    public void setMinZ(double minZ) {
        this.minZ = minZ;
    }

    public double getMaxZ() {
        return maxZ;
    }

    public void setMaxZ(double maxZ) {
        this.maxZ = maxZ;
    }
}

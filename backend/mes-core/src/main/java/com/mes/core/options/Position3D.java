package com.mes.core.options;

/**
 * 3D 场景坐标(米)与绕 Y 轴旋转角(度)。
 * 前端 3D 车间据此摆放设备,调整布局只需改配置文件。
 */
public class Position3D {

    private double x;
    private double y;
    private double z;

    /** 绕竖直轴的旋转角度(度)。 */
    private double rotationY;

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public double getRotationY() {
        return rotationY;
    }

    public void setRotationY(double rotationY) {
        this.rotationY = rotationY;
    }
}

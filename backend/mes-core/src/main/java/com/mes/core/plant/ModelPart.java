package com.mes.core.plant;

/**
 * 设备模型的一个构件。
 * <p>
 * 坐标以设备本体为原点:X 沿设备长度方向、Z 沿宽度方向、Y 为离地高度,
 * 因此同一份构件描述可以随设备整体旋转,不必为每个朝向写一遍。
 * <p>
 * {@link #kind} 支持的取值(前端按此分派几何体与材质):
 * <ul>
 *   <li>{@code box} —— 长方体,机身/柜体/台面,用 length/height/width;</li>
 *   <li>{@code cylinder} —— 圆柱,电机/主轴/搅拌桶/储罐,用 radius/height + axis;</li>
 *   <li>{@code glass} —— 半透明板或罩,亚克力封板与观察窗;</li>
 *   <li>{@code screen} —— HMI 触摸屏,深色薄板;</li>
 *   <li>{@code tower} —— 三色状态塔灯,顶段颜色跟随设备实时状态;</li>
 *   <li>{@code rotor} —— 运行动件,设备处于运行状态时持续旋转;</li>
 *   <li>{@code frame} —— 铝型材框架,只画包围盒的棱;</li>
 *   <li>{@code pipe} —— 管路,细圆柱,默认管路配色;</li>
 *   <li>{@code duct} —— 风管/排气烟囱,粗圆柱,默认镀锌色。</li>
 * </ul>
 */
public class ModelPart {

    /** 构件类型,见类注释。 */
    private String kind = "box";

    /** 构件名称,仅用于配置可读性(如 "压芯机构"、"三色灯")。 */
    private String name = "";

    /** 相对设备原点的 X 偏移(米)。 */
    private double x;

    /** 离地高度(米),指构件几何中心的高度。 */
    private double y;

    /** 相对设备原点的 Z 偏移(米)。 */
    private double z;

    /** 长方体沿 X 的尺寸(米)。 */
    private double length;

    /** 高度(米),长方体与圆柱共用。 */
    private double height;

    /** 长方体沿 Z 的尺寸(米)。 */
    private double width;

    /** 圆柱/球半径(米)。 */
    private double radius;

    /** 圆柱轴向:x / y / z。 */
    private String axis = "y";

    /** 构件颜色(#RRGGBB),留空继承设备主色。 */
    private String color = "";

    /** 不透明度,0~1,小于 1 时按透明材质渲染。 */
    private double opacity = 1.0;

    /** 构件自身绕竖直轴的旋转角(度)。 */
    private double rotationY;

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

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

    public double getLength() {
        return length;
    }

    public void setLength(double length) {
        this.length = length;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    public String getAxis() {
        return axis;
    }

    public void setAxis(String axis) {
        this.axis = axis;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public double getOpacity() {
        return opacity;
    }

    public void setOpacity(double opacity) {
        this.opacity = opacity;
    }

    public double getRotationY() {
        return rotationY;
    }

    public void setRotationY(double rotationY) {
        this.rotationY = rotationY;
    }
}

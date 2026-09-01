package com.mes.core.plant;

import java.util.ArrayList;
import java.util.List;

/**
 * 建筑轴网:纵向(字母轴)与横向(数字轴)两组轴线,交点即柱位。
 * 3D 场景据此生成柱子与轴号标牌,现场看图的人可以按轴号定位设备。
 */
public class AxisGrid {

    /** 柱截面边长(米),按图纸柱子尺寸。 */
    private double columnSize = 1.0;

    /** 柱子高度(米),默认与外墙同高。 */
    private double columnHeight = 6.0;

    /**
     * 沿 X 方向排布的轴线(图纸上的字母轴,如 3-A ~ 3-F)。
     * <p>
     * 命名为 axesX 而非 xAxes 是必要的:Jackson 还原 {@code getXAxes()} 的属性名时,
     * 会把开头连续的大写字母一起转小写,得到 "xaxes",与配置文件和前端约定的键名都对不上。
     */
    private List<AxisLine> axesX = new ArrayList<>();

    /** 沿 Z 方向排布的轴线(图纸上的数字轴,如 3-1 ~ 3-4)。 */
    private List<AxisLine> axesZ = new ArrayList<>();

    public double getColumnSize() {
        return columnSize;
    }

    public void setColumnSize(double columnSize) {
        this.columnSize = columnSize;
    }

    public double getColumnHeight() {
        return columnHeight;
    }

    public void setColumnHeight(double columnHeight) {
        this.columnHeight = columnHeight;
    }

    public List<AxisLine> getAxesX() {
        return axesX;
    }

    public void setAxesX(List<AxisLine> axesX) {
        this.axesX = axesX;
    }

    public List<AxisLine> getAxesZ() {
        return axesZ;
    }

    public void setAxesZ(List<AxisLine> axesZ) {
        this.axesZ = axesZ;
    }
}

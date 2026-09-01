package com.mes.core.plant;

/**
 * 一条建筑轴线:图纸上的轴号 + 该轴在场景中的坐标(米)。
 * 纵轴取 X 坐标,横轴取 Z 坐标,由所在集合决定。
 */
public class AxisLine {

    /** 图纸轴号,如 "3-A"、"3-1"。 */
    private String label = "";

    /** 轴线坐标(米)。 */
    private double value;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }
}

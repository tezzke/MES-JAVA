package com.mes.core.model;

import com.mes.core.enums.DataQuality;

/**
 * 单个点位的一次实时值(内存模型,不入库)。
 */
public class PointValue {

    /** 点位编码。 */
    private String name = "";

    /** 点位显示名。 */
    private String displayName = "";

    /** 换算后的工程值。 */
    private double value;

    /** 工程单位。 */
    private String unit = "";

    /** 数据质量。 */
    private DataQuality quality = DataQuality.Good;

    public PointValue() {
    }

    public PointValue(String name, String displayName, double value, String unit, DataQuality quality) {
        this.name = name;
        this.displayName = displayName;
        this.value = value;
        this.unit = unit;
        this.quality = quality;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public DataQuality getQuality() {
        return quality;
    }

    public void setQuality(DataQuality quality) {
        this.quality = quality;
    }
}

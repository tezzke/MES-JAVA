package com.mes.core.options;

import com.mes.core.enums.RegisterDataType;

/**
 * 采集点位配置:描述"从哪个寄存器、按什么类型、如何换算"读取一个业务量。
 * 同时承载数据校验(量程)与报警(阈值)规则,是数据正确性的第一道防线。
 */
public class PointConfig {

    /** 点位编码(设备内唯一),如 "Temperature"。入库与 API 均使用该编码。 */
    private String name = "";

    /** 点位显示名,如 "主轴温度"。 */
    private String displayName = "";

    /** 工程单位,如 "℃"、"rpm"、"A"。 */
    private String unit = "";

    /** 寄存器起始地址(0 基址,即 PLC 报文地址)。 */
    private int address;

    /** Modbus 功能码:3 = 读保持寄存器(4x),4 = 读输入寄存器(3x)。 */
    private int functionCode = 3;

    /** 寄存器数据类型,决定占用寄存器数量与解码方式。 */
    private RegisterDataType dataType = RegisterDataType.UInt16;

    /** 换算系数:工程值 = 原始值 × scale + offset。如寄存器存放"温度×10"则 scale=0.1。 */
    private double scale = 1;

    /** 换算偏移量。 */
    private double offset;

    /** 是否为"设备状态字"点位(数值含义见 DeviceStatus 枚举)。每台设备应恰好配置一个。 */
    private boolean isStatus;

    /** 是否为"累计产量"点位,前端统计总产量时使用。 */
    private boolean isCounter;

    // ---------- 数据校验(量程) ----------

    /** 物理量程下限:低于该值判定为 Uncertain 质量。null 表示不校验。 */
    private Double rangeMin;

    /** 物理量程上限:高于该值判定为 Uncertain 质量。null 表示不校验。 */
    private Double rangeMax;

    // ---------- 报警阈值 ----------

    /** 低报阈值:工程值低于该值时产生报警。null 表示不启用。 */
    private Double alarmLow;

    /** 高报阈值:工程值高于该值时产生报警。null 表示不启用。 */
    private Double alarmHigh;

    /**
     * 报警滞回死区:数值回到"阈值 ± 死区"以内才算恢复,
     * 防止数值在阈值附近抖动导致报警反复触发/恢复刷屏。
     * null 时自动取阈值绝对值的 2%。
     */
    private Double alarmDeadband;

    /** 获取某阈值对应的实际死区宽度。 */
    public double deadbandFor(double threshold) {
        return alarmDeadband != null ? alarmDeadband : Math.abs(threshold) * 0.02;
    }

    /** 该点位占用的寄存器个数。 */
    public int registerCount() {
        return dataType.registerCount();
    }

    /** 该点位覆盖的尾后地址(起始地址 + 寄存器数)。 */
    public int endAddress() {
        return address + registerCount();
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

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public int getAddress() {
        return address;
    }

    public void setAddress(int address) {
        this.address = address;
    }

    public int getFunctionCode() {
        return functionCode;
    }

    public void setFunctionCode(int functionCode) {
        this.functionCode = functionCode;
    }

    public RegisterDataType getDataType() {
        return dataType;
    }

    public void setDataType(RegisterDataType dataType) {
        this.dataType = dataType;
    }

    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        this.scale = scale;
    }

    public double getOffset() {
        return offset;
    }

    public void setOffset(double offset) {
        this.offset = offset;
    }

    public boolean getIsStatus() {
        return isStatus;
    }

    public void setIsStatus(boolean isStatus) {
        this.isStatus = isStatus;
    }

    public boolean getIsCounter() {
        return isCounter;
    }

    public void setIsCounter(boolean isCounter) {
        this.isCounter = isCounter;
    }

    public Double getRangeMin() {
        return rangeMin;
    }

    public void setRangeMin(Double rangeMin) {
        this.rangeMin = rangeMin;
    }

    public Double getRangeMax() {
        return rangeMax;
    }

    public void setRangeMax(Double rangeMax) {
        this.rangeMax = rangeMax;
    }

    public Double getAlarmLow() {
        return alarmLow;
    }

    public void setAlarmLow(Double alarmLow) {
        this.alarmLow = alarmLow;
    }

    public Double getAlarmHigh() {
        return alarmHigh;
    }

    public void setAlarmHigh(Double alarmHigh) {
        this.alarmHigh = alarmHigh;
    }

    public Double getAlarmDeadband() {
        return alarmDeadband;
    }

    public void setAlarmDeadband(Double alarmDeadband) {
        this.alarmDeadband = alarmDeadband;
    }
}

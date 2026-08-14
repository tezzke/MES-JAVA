package com.mes.core.entity;

import java.time.Instant;

/**
 * 扫码记录(数据库实体),用于生产追溯:
 * 记录"哪个条码、在哪台设备/工位、什么时间"被扫描。
 */
public class BarcodeRecord {

    /** 自增主键。 */
    private long id;

    /** 扫码枪标识(来源 IP 或虚拟编号)。 */
    private String scannerId = "";

    /** 绑定的设备编码(由扫码枪绑定表映射),未绑定为空字符串。 */
    private String deviceId = "";

    /** 条码内容(产品序列号 / 工单号)。 */
    private String barcode = "";

    /** 扫码时间(UTC 时刻)。 */
    private Instant scannedAt = Instant.EPOCH;

    public BarcodeRecord() {
    }

    public BarcodeRecord(String scannerId, String deviceId, String barcode, Instant scannedAt) {
        this.scannerId = scannerId;
        this.deviceId = deviceId;
        this.barcode = barcode;
        this.scannedAt = scannedAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getScannerId() {
        return scannerId;
    }

    public void setScannerId(String scannerId) {
        this.scannerId = scannerId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public Instant getScannedAt() {
        return scannedAt;
    }

    public void setScannedAt(Instant scannedAt) {
        this.scannedAt = scannedAt;
    }
}

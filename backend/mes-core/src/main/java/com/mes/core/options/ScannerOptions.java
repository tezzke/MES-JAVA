package com.mes.core.options;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 扫码枪接入配置,绑定自 devices.json 的 "Scanner" 节点。
 * 网口扫码枪通常以 TCP Client 模式主动连接服务器并推送条码文本,
 * 因此采集层开一个 TCP 监听端口统一接收;模拟模式下由定时器生成假条码。
 */
public class ScannerOptions {

    /** 配置节点名称。 */
    public static final String SECTION_NAME = "Scanner";

    /** 是否启用扫码枪接入。 */
    private boolean enabled = true;

    /** TCP 监听端口,所有扫码枪连到该端口。 */
    private int listenPort = 6001;

    /** 模拟模式下,生成假条码的间隔(毫秒)。 */
    private int simulateIntervalMs = 8000;

    /**
     * 扫码枪清单:key = 扫码枪来源 IP(模拟模式下为虚拟编号),value = 绑定的设备编码。
     * 用于把条码归属到某台设备/工位,实现生产追溯。
     */
    private Map<String, String> scannerBindings = new LinkedHashMap<>();

    /** 查询某把扫码枪绑定的设备编码,未绑定返回空字符串。 */
    public String deviceOf(String scannerId) {
        return scannerBindings.getOrDefault(scannerId, "");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getListenPort() {
        return listenPort;
    }

    public void setListenPort(int listenPort) {
        this.listenPort = listenPort;
    }

    public int getSimulateIntervalMs() {
        return simulateIntervalMs;
    }

    public void setSimulateIntervalMs(int simulateIntervalMs) {
        this.simulateIntervalMs = simulateIntervalMs;
    }

    public Map<String, String> getScannerBindings() {
        return scannerBindings;
    }

    public void setScannerBindings(Map<String, String> scannerBindings) {
        this.scannerBindings = scannerBindings;
    }
}

package com.mes.acquisition.modbus;

import java.io.IOException;

/** 带有已收发 MBAP 报文上下文的通讯异常。 */
public class ModbusFrameException extends IOException {

    private final int transactionId;
    private final String requestHex;
    private final String responseHex;

    public ModbusFrameException(String message, Throwable cause, int transactionId,
                                String requestHex, String responseHex) {
        super(message, cause);
        this.transactionId = transactionId;
        this.requestHex = requestHex;
        this.responseHex = responseHex;
    }

    public int transactionId() {
        return transactionId;
    }

    public String requestHex() {
        return requestHex;
    }

    public String responseHex() {
        return responseHex;
    }
}

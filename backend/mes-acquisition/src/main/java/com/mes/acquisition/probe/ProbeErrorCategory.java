package com.mes.acquisition.probe;

/** 现场探针向 API 暴露的稳定错误分类。 */
public enum ProbeErrorCategory {
    VALIDATION,
    SECURITY,
    BUSY,
    DNS,
    TIMEOUT,
    CONNECTION_REFUSED,
    MODBUS_EXCEPTION,
    PROTOCOL,
    IO,
    INTERNAL
}

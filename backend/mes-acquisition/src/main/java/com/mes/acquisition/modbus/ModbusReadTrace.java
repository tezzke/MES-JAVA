package com.mes.acquisition.modbus;

/** 一次 Modbus 读取的协议级跟踪信息。 */
public record ModbusReadTrace(
        int transactionId,
        String requestHex,
        String responseHex,
        int[] registers) {

    public ModbusReadTrace {
        registers = registers == null ? new int[0] : registers.clone();
    }

    @Override
    public int[] registers() {
        return registers.clone();
    }
}

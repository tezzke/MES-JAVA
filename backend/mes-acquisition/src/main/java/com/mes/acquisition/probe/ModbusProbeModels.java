package com.mes.acquisition.probe;

import com.mes.acquisition.modbus.RegisterByteOrder;
import com.mes.acquisition.modbus.RegisterWordOrder;
import com.mes.core.enums.RegisterDataType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Modbus 现场探针的模块内契约。 */
public final class ModbusProbeModels {

    private ModbusProbeModels() {
    }

    public record ConnectRequest(String host, int port, Integer timeoutMs) {
    }

    public record ReadRequest(
            String host,
            int port,
            Integer timeoutMs,
            int unitId,
            int functionCode,
            int startAddress,
            int count,
            RegisterDataType dataType,
            Integer decodeIndex,
            RegisterByteOrder byteOrder,
            RegisterWordOrder wordOrder,
            Double scale,
            Double offset) {
    }

    public record SessionRequest(ReadRequest read, int intervalMs, int durationSeconds) {
    }

    public record ConnectResult(
            boolean success,
            String resolvedAddress,
            long elapsedMs,
            ProbeErrorCategory errorCategory,
            String errorMessage) {
    }

    public record ReadResult(
            Instant timestamp,
            boolean success,
            Integer transactionId,
            String requestHex,
            String responseHex,
            int[] registers,
            Double rawValue,
            Double parsedValue,
            double scale,
            double offset,
            long elapsedMs,
            ProbeErrorCategory errorCategory,
            String errorMessage) {

        public ReadResult {
            registers = registers == null ? new int[0] : registers.clone();
        }

        @Override
        public int[] registers() {
            return registers.clone();
        }
    }

    public enum SessionStatus {
        RUNNING,
        COMPLETED,
        STOPPED,
        FAILED
    }

    public record SessionView(
            UUID id,
            SessionStatus status,
            Instant startedAt,
            Instant finishedAt,
            int intervalMs,
            int durationSeconds,
            List<ReadResult> samples) {

        public SessionView {
            samples = List.copyOf(samples);
        }
    }
}

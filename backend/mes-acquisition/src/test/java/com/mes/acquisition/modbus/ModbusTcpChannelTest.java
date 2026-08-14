package com.mes.acquisition.modbus;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.net.ServerSocket;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用最小假从站验证 MBAP 跟踪和寄存器读取。 */
class ModbusTcpChannelTest {

    @Test
    void capturesRequestResponseAndTransactionId() throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             var executor = Executors.newSingleThreadExecutor()) {
            var exchange = executor.submit(() -> {
                try (var socket = server.accept()) {
                    byte[] request = new DataInputStream(socket.getInputStream()).readNBytes(12);
                    byte[] response = {
                            request[0], request[1], 0, 0, 0, 7,
                            request[6], request[7], 4,
                            0x12, 0x34, (byte) 0xAB, (byte) 0xCD
                    };
                    socket.getOutputStream().write(response);
                    socket.getOutputStream().flush();
                    return request;
                }
            });

            try (ModbusTcpChannel channel = new ModbusTcpChannel(
                    "127.0.0.1", server.getLocalPort(), 1000,
                    LoggerFactory.getLogger(ModbusTcpChannelTest.class))) {
                ModbusReadTrace trace = channel.readRegistersWithTrace(1, 3, 100, 2);

                assertThat(trace.transactionId()).isEqualTo(1);
                assertThat(trace.registers()).containsExactly(0x1234, 0xABCD);
                assertThat(trace.requestHex()).isEqualTo(
                        "00 01 00 00 00 06 01 03 00 64 00 02");
                assertThat(trace.responseHex()).isEqualTo(
                        "00 01 00 00 00 07 01 03 04 12 34 AB CD");
            }
            assertThat(exchange.get(1, TimeUnit.SECONDS)).hasSize(12);
        }
    }
}

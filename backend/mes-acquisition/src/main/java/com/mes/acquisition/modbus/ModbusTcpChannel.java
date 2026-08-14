package com.mes.acquisition.modbus;

import org.slf4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HexFormat;

/**
 * ModbusTCP 通讯通道:直接按 Modbus Application Protocol 规范收发报文,不引入第三方协议库。
 * <p>
 * 本系统只读设备(功能码 03 读保持寄存器 / 04 读输入寄存器),报文结构极简,
 * 自行实现可以完全掌控超时与重连语义,也避免为一个读操作背上整套协议栈依赖。
 * <p>
 * 特性:
 * <ul>
 *   <li>懒连接:首次读取时才建立 TCP 连接;</li>
 *   <li>自愈:任何通讯异常都会销毁连接,下次读取自动重连;</li>
 *   <li>线程安全:方法级串行化(Modbus 从站不支持并发请求)。</li>
 * </ul>
 *
 * <pre>
 * 请求帧(12 字节):
 *   [事务号 2][协议号 2 = 0][后续长度 2 = 6][从站号 1][功能码 1][起始地址 2][寄存器数 2]
 * 响应帧:
 *   [事务号 2][协议号 2][后续长度 2][从站号 1][功能码 1][字节数 1][数据 N×2]
 *   功能码最高位置 1 表示异常响应,其后一字节为异常码。
 * </pre>
 */
public final class ModbusTcpChannel implements AutoCloseable {

    /** Modbus 协议单次读取的寄存器数上限。 */
    private static final int MAX_REGISTERS_PER_READ = 125;

    /** MBAP 报文头长度。 */
    private static final int MBAP_HEADER_LENGTH = 6;

    private final String ip;
    private final int port;
    private int timeoutMs;
    private final Logger logger;

    private Socket socket;
    private DataInputStream input;
    private OutputStream output;
    private int transactionId;
    private int currentTransactionId;
    private String currentRequestHex;

    public ModbusTcpChannel(String ip, int port, int timeoutMs, Logger logger) {
        this.ip = ip;
        this.port = port;
        this.timeoutMs = timeoutMs;
        this.logger = logger;
    }

    /**
     * 读取一段寄存器。
     *
     * @param unitId       从站号。
     * @param functionCode 3 = 保持寄存器,4 = 输入寄存器。
     * @param startAddress 起始地址(0 基址)。
     * @param count        寄存器个数。
     * @return 长度为 count 的无符号 16 位寄存器值数组。
     * @throws IOException 通讯失败或从站返回异常响应;由采集器统一捕获并标记设备离线。
     */
    public synchronized int[] readRegisters(int unitId, int functionCode, int startAddress, int count)
            throws IOException {
        return readRegistersWithTrace(unitId, functionCode, startAddress, count).registers();
    }

    /**
     * 读取寄存器并返回完整请求/响应报文，供现场探针诊断使用。
     * 正式采集仍调用 {@link #readRegisters(int, int, int, int)}。
     */
    public synchronized ModbusReadTrace readRegistersWithTrace(
            int unitId, int functionCode, int startAddress, int count) throws IOException {
        if (count < 1 || count > MAX_REGISTERS_PER_READ) {
            throw new IOException("单次读取寄存器数越界:" + count);
        }
        if (functionCode != 3 && functionCode != 4) {
            throw new IOException("仅支持只读功能码 03/04,当前配置为:" + functionCode);
        }

        currentTransactionId = 0;
        currentRequestHex = null;
        try {
            ensureConnected();
            return request(unitId, functionCode, startAddress, count);
        } catch (IOException ex) {
            // 通讯失败:销毁连接,下次读取时自动重连
            closeConnection();
            if (!(ex instanceof ModbusFrameException) && currentRequestHex != null) {
                throw new ModbusFrameException(ex.getMessage(), ex, currentTransactionId,
                        currentRequestHex, null);
            }
            throw ex;
        }
    }

    /** 只建立 TCP 连接，不发送 Modbus 请求。 */
    public synchronized void connectTest() throws IOException {
        try {
            ensureConnected();
        } catch (IOException ex) {
            closeConnection();
            throw ex;
        }
    }

    /**
     * 调整后续连接和读取超时。探针轮询在接近会话截止时间时使用，
     * 正式采集无需调用。
     */
    public synchronized void setTimeoutMs(int timeoutMs) throws IOException {
        if (timeoutMs < 1) {
            throw new IOException("ModbusTCP 超时必须大于 0");
        }
        this.timeoutMs = timeoutMs;
        if (socket != null && !socket.isClosed()) {
            socket.setSoTimeout(timeoutMs);
        }
    }

    /** 确保 TCP 连接可用,必要时重建。 */
    private void ensureConnected() throws IOException {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            return;
        }

        closeConnection();
        logger.info("正在连接 ModbusTCP 从站 {}:{} ...", ip, port);

        Socket client = new Socket();
        try {
            client.setTcpNoDelay(true);
            client.setSoTimeout(timeoutMs);
            // 限制连接超时,避免默认的数十秒 TCP 等待拖垮采集节拍
            client.connect(new InetSocketAddress(ip, port), timeoutMs);
        } catch (IOException ex) {
            closeQuietly(client);
            throw ex;
        }

        socket = client;
        input = new DataInputStream(client.getInputStream());
        output = client.getOutputStream();
        logger.info("ModbusTCP 从站 {}:{} 连接成功", ip, port);
    }

    /** 发送一次读请求并解析响应。 */
    private ModbusReadTrace request(int unitId, int functionCode, int startAddress, int count)
            throws IOException {
        int tid = nextTransactionId();

        byte[] frame = new byte[MBAP_HEADER_LENGTH + 6];
        writeUInt16(frame, 0, tid);
        writeUInt16(frame, 2, 0);            // 协议标识固定为 0
        writeUInt16(frame, 4, 6);            // 后续字节数:从站号 + 功能码 + 地址 + 数量
        frame[6] = (byte) unitId;
        frame[7] = (byte) functionCode;
        writeUInt16(frame, 8, startAddress);
        writeUInt16(frame, 10, count);
        currentTransactionId = tid;
        currentRequestHex = toHex(frame);
        output.write(frame);
        output.flush();

        // ---- 响应头 ----
        byte[] header = new byte[MBAP_HEADER_LENGTH];
        input.readFully(header);
        int length = readUInt16(header, 4);
        if (length < 2 || length > 256) {
            throw frameException("ModbusTCP 响应长度非法:" + length,
                    tid, frame, header, null);
        }

        byte[] body = new byte[length];
        input.readFully(body);
        byte[] responseFrame = join(header, body);

        int responseTransactionId = readUInt16(header, 0);
        int protocolId = readUInt16(header, 2);
        if (responseTransactionId != tid) {
            throw frameException("ModbusTCP 响应事务号不匹配:期望 " + tid
                    + ",实际 " + responseTransactionId, tid, frame, responseFrame, null);
        }
        if (protocolId != 0) {
            throw frameException("ModbusTCP 响应协议标识非法:" + protocolId,
                    tid, frame, responseFrame, null);
        }

        int responseUnitId = body[0] & 0xFF;
        int responseFunction = body[1] & 0xFF;
        if (responseUnitId != unitId) {
            throw frameException("ModbusTCP 响应从站号不匹配:期望 " + unitId
                    + ",实际 " + responseUnitId, tid, frame, responseFrame, null);
        }
        if ((responseFunction & 0x80) != 0) {
            int exceptionCode = length > 2 ? body[2] & 0xFF : 0;
            throw frameException("从站返回 Modbus 异常码 " + exceptionCode
                    + "(功能码 " + functionCode + ")", tid, frame, responseFrame, null);
        }
        if (responseFunction != functionCode) {
            throw frameException("ModbusTCP 响应功能码不匹配:期望 " + functionCode
                    + ",实际 " + responseFunction, tid, frame, responseFrame, null);
        }
        if (length < 3) {
            throw frameException("ModbusTCP 响应缺少字节数", tid, frame, responseFrame, null);
        }

        int byteCount = body[2] & 0xFF;
        if (byteCount != count * 2 || length < 3 + byteCount) {
            throw frameException("ModbusTCP 响应数据长度异常:字节数 " + byteCount
                    + ",期望 " + (count * 2), tid, frame, responseFrame, null);
        }

        int[] registers = new int[count];
        for (int i = 0; i < count; i++) {
            registers[i] = readUInt16(body, 3 + i * 2);
        }
        return new ModbusReadTrace(tid, toHex(frame), toHex(responseFrame), registers);
    }

    private static byte[] join(byte[] first, byte[] second) {
        byte[] joined = new byte[first.length + second.length];
        System.arraycopy(first, 0, joined, 0, first.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }

    private static ModbusFrameException frameException(
            String message, int tid, byte[] request, byte[] response, Throwable cause) {
        return new ModbusFrameException(message, cause, tid, toHex(request),
                response == null ? null : toHex(response));
    }

    private static String toHex(byte[] bytes) {
        return HexFormat.ofDelimiter(" ").withUpperCase().formatHex(bytes);
    }

    private int nextTransactionId() {
        transactionId = (transactionId + 1) & 0xFFFF;
        return transactionId;
    }

    private static void writeUInt16(byte[] buffer, int offset, int value) {
        buffer[offset] = (byte) ((value >> 8) & 0xFF);
        buffer[offset + 1] = (byte) (value & 0xFF);
    }

    private static int readUInt16(byte[] buffer, int offset) {
        return ((buffer[offset] & 0xFF) << 8) | (buffer[offset + 1] & 0xFF);
    }

    private void closeConnection() {
        closeQuietly(socket);
        socket = null;
        input = null;
        output = null;
    }

    private static void closeQuietly(Socket target) {
        if (target != null) {
            try {
                target.close();
            } catch (IOException ignored) {
                // 关闭异常无意义,忽略
            }
        }
    }

    @Override
    public synchronized void close() {
        closeConnection();
    }
}

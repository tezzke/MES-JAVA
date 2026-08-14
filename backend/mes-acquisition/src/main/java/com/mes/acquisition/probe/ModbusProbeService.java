package com.mes.acquisition.probe;

import com.mes.acquisition.modbus.ModbusFrameException;
import com.mes.acquisition.modbus.ModbusReadTrace;
import com.mes.acquisition.modbus.ModbusTcpChannel;
import com.mes.acquisition.modbus.RegisterByteOrder;
import com.mes.acquisition.modbus.RegisterDecoder;
import com.mes.acquisition.modbus.RegisterWordOrder;
import com.mes.core.enums.RegisterDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.mes.acquisition.probe.ModbusProbeModels.*;

/**
 * Modbus 现场探针。每次操作使用独立临时连接，不共享正式采集通道；
 * 后台会话由信号量限制并发，最长运行 60 秒。
 */
@Service
@Lazy
public class ModbusProbeService implements DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModbusProbeService.class);
    private static final Duration FINISHED_RETENTION = Duration.ofMinutes(5);

    private final ModbusProbeNetworkPolicy networkPolicy;
    private final Semaphore permits;
    private final ExecutorService workers;
    private final ScheduledExecutorService cleanup;
    private final Map<UUID, SessionState> sessions = new ConcurrentHashMap<>();

    public ModbusProbeService(ModbusProbeNetworkPolicy networkPolicy,
                              @Value("${mes.modbus-probe.max-concurrent:2}") int maxConcurrent) {
        if (maxConcurrent < 1 || maxConcurrent > 16) {
            throw new IllegalArgumentException("mes.modbus-probe.max-concurrent 必须在 1..16");
        }
        this.networkPolicy = networkPolicy;
        this.permits = new Semaphore(maxConcurrent);
        this.workers = Executors.newFixedThreadPool(maxConcurrent,
                Thread.ofPlatform().name("modbus-probe-", 0).factory());
        this.cleanup = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("modbus-probe-cleanup-", 0).factory());
        this.cleanup.scheduleAtFixedRate(this::cleanupFinished, 1, 1, TimeUnit.MINUTES);
    }

    public ConnectResult connectTest(ConnectRequest request) {
        int timeout = validateConnect(request);
        String address = networkPolicy.validateAndResolve(request.host(), request.port());
        acquire();
        long started = System.nanoTime();
        try (ModbusTcpChannel channel = new ModbusTcpChannel(
                address, request.port(), timeout, LOGGER)) {
            channel.connectTest();
            return new ConnectResult(true, address, elapsedMs(started), null, null);
        } catch (Exception ex) {
            return new ConnectResult(false, address, elapsedMs(started),
                    classify(ex), safeMessage(ex));
        } finally {
            permits.release();
        }
    }

    public ReadResult read(ReadRequest request) {
        ValidatedRead validated = validateRead(request);
        acquire();
        try (ModbusTcpChannel channel = new ModbusTcpChannel(
                validated.address(), request.port(), validated.timeoutMs(), LOGGER)) {
            return executeRead(request, validated, channel);
        } finally {
            permits.release();
        }
    }

    public SessionView startSession(SessionRequest request) {
        if (request == null || request.read() == null) {
            throw new ProbeException(ProbeErrorCategory.VALIDATION, "read 不能为空");
        }
        if (request.durationSeconds() < 1 || request.durationSeconds() > 60) {
            throw new ProbeException(ProbeErrorCategory.VALIDATION, "durationSeconds 必须在 1..60");
        }
        if (request.intervalMs() < 100 || request.intervalMs() > 10_000) {
            throw new ProbeException(ProbeErrorCategory.VALIDATION, "intervalMs 必须在 100..10000");
        }
        ValidatedRead validated = validateRead(request.read());
        acquire();
        UUID id = UUID.randomUUID();
        SessionState state = new SessionState(id, request.intervalMs(), request.durationSeconds());
        sessions.put(id, state);
        try {
            state.future = workers.submit(() -> runSession(state, request.read(), validated));
        } catch (RuntimeException ex) {
            sessions.remove(id);
            permits.release();
            throw ex;
        }
        return state.view();
    }

    public SessionView getSession(UUID id) {
        SessionState state = sessions.get(id);
        if (state == null) {
            throw new ProbeException(ProbeErrorCategory.VALIDATION, "探针会话不存在或已清理");
        }
        return state.view();
    }

    public SessionView stopSession(UUID id) {
        SessionState state = sessions.get(id);
        if (state == null) {
            throw new ProbeException(ProbeErrorCategory.VALIDATION, "探针会话不存在或已清理");
        }
        state.stopRequested = true;
        Future<?> future = state.future;
        if (future != null && state.taskStarted) {
            future.cancel(true);
        }
        if (state.status == SessionStatus.RUNNING) {
            state.status = SessionStatus.STOPPED;
            state.finishedAt = Instant.now();
        }
        return state.view();
    }

    private void runSession(SessionState state, ReadRequest request, ValidatedRead validated) {
        state.taskStarted = true;
        Instant deadline = state.startedAt.plusSeconds(state.durationSeconds);
        try (ModbusTcpChannel channel = new ModbusTcpChannel(
                validated.address(), request.port(), validated.timeoutMs(), LOGGER)) {
            while (!state.stopRequested && Instant.now().isBefore(deadline)) {
                long beforeRead = Duration.between(Instant.now(), deadline).toMillis();
                if (beforeRead <= 0) {
                    break;
                }
                channel.setTimeoutMs((int) Math.min(validated.timeoutMs(),
                        Math.max(1L, beforeRead)));
                state.samples.add(executeRead(request, validated, channel));
                long remaining = Duration.between(Instant.now(), deadline).toMillis();
                if (remaining > 0) {
                    Thread.sleep(Math.min(state.intervalMs, remaining));
                }
            }
            state.status = state.stopRequested ? SessionStatus.STOPPED : SessionStatus.COMPLETED;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            state.status = SessionStatus.STOPPED;
        } catch (Exception ex) {
            state.samples.add(failedResult(request, System.nanoTime(), ex));
            state.status = SessionStatus.FAILED;
        } finally {
            state.finishedAt = Instant.now();
            permits.release();
        }
    }

    private ReadResult executeRead(ReadRequest request, ValidatedRead validated,
                                   ModbusTcpChannel channel) {
        long started = System.nanoTime();
        double scale = valueOr(request.scale(), 1d);
        double offset = valueOr(request.offset(), 0d);
        try {
            ModbusReadTrace trace = channel.readRegistersWithTrace(
                    request.unitId(), request.functionCode(), request.startAddress(), request.count());
            double raw = RegisterDecoder.decode(trace.registers(), validated.decodeIndex(),
                    validated.dataType(), validated.byteOrder(), validated.wordOrder());
            return new ReadResult(Instant.now(), true, trace.transactionId(),
                    trace.requestHex(), trace.responseHex(), trace.registers(), raw,
                    raw * scale + offset, scale, offset, elapsedMs(started), null, null);
        } catch (Exception ex) {
            return failedResult(request, started, ex);
        }
    }

    private ReadResult failedResult(ReadRequest request, long started, Exception ex) {
        ModbusFrameException frame = ex instanceof ModbusFrameException value ? value : null;
        return new ReadResult(Instant.now(), false,
                frame == null ? null : frame.transactionId(),
                frame == null ? null : frame.requestHex(),
                frame == null ? null : frame.responseHex(), new int[0],
                null, null, valueOr(request.scale(), 1d), valueOr(request.offset(), 0d),
                elapsedMs(started), classify(ex), safeMessage(ex));
    }

    private int validateConnect(ConnectRequest request) {
        if (request == null) {
            throw new ProbeException(ProbeErrorCategory.VALIDATION, "请求不能为空");
        }
        return validateTimeout(request.timeoutMs());
    }

    private ValidatedRead validateRead(ReadRequest request) {
        if (request == null) {
            throw new ProbeException(ProbeErrorCategory.VALIDATION, "请求不能为空");
        }
        int timeout = validateTimeout(request.timeoutMs());
        if (request.functionCode() != 3 && request.functionCode() != 4) {
            throw new ProbeException(ProbeErrorCategory.VALIDATION, "仅支持功能码 03/04");
        }
        if (request.unitId() < 0 || request.unitId() > 255
                || request.startAddress() < 0 || request.startAddress() > 65535
                || request.count() < 1 || request.count() > 125
                || request.startAddress() + request.count() > 65536) {
            throw new ProbeException(ProbeErrorCategory.VALIDATION, "从站号、地址或寄存器数量越界");
        }
        RegisterDataType type = request.dataType() == null ? RegisterDataType.UInt16 : request.dataType();
        int index = request.decodeIndex() == null ? 0 : request.decodeIndex();
        if (index < 0 || index + type.registerCount() > request.count()) {
            throw new ProbeException(ProbeErrorCategory.VALIDATION, "解码范围超出读取寄存器");
        }
        double scale = valueOr(request.scale(), 1d);
        double offset = valueOr(request.offset(), 0d);
        if (!Double.isFinite(scale) || !Double.isFinite(offset)) {
            throw new ProbeException(ProbeErrorCategory.VALIDATION, "scale/offset 必须为有限数值");
        }
        String address = networkPolicy.validateAndResolve(request.host(), request.port());
        return new ValidatedRead(address, timeout, type, index,
                request.byteOrder() == null ? RegisterByteOrder.BIG_ENDIAN : request.byteOrder(),
                request.wordOrder() == null ? RegisterWordOrder.HIGH_WORD_FIRST : request.wordOrder());
    }

    private static int validateTimeout(Integer value) {
        int timeout = value == null ? 3000 : value;
        if (timeout < 100 || timeout > 10_000) {
            throw new ProbeException(ProbeErrorCategory.VALIDATION, "timeoutMs 必须在 100..10000");
        }
        return timeout;
    }

    private void acquire() {
        if (!permits.tryAcquire()) {
            throw new ProbeException(ProbeErrorCategory.BUSY, "Modbus 探针并发已达上限");
        }
    }

    private static ProbeErrorCategory classify(Throwable ex) {
        if (ex instanceof ProbeException probe) {
            return probe.category();
        }
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) {
                return ProbeErrorCategory.TIMEOUT;
            }
            if (cause instanceof ConnectException) {
                return ProbeErrorCategory.CONNECTION_REFUSED;
            }
        }
        if (ex instanceof IOException) {
            String message = ex.getMessage() == null ? "" : ex.getMessage();
            if (message.contains("Modbus 异常码")) {
                return ProbeErrorCategory.MODBUS_EXCEPTION;
            }
            if (message.contains("响应") || message.contains("功能码")) {
                return ProbeErrorCategory.PROTOCOL;
            }
            return ProbeErrorCategory.IO;
        }
        return ProbeErrorCategory.INTERNAL;
    }

    private static String safeMessage(Throwable ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private static long elapsedMs(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private static double valueOr(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private void cleanupFinished() {
        Instant cutoff = Instant.now().minus(FINISHED_RETENTION);
        sessions.entrySet().removeIf(entry -> entry.getValue().finishedAt != null
                && entry.getValue().finishedAt.isBefore(cutoff));
    }

    @Override
    public void destroy() {
        sessions.values().forEach(state -> {
            state.stopRequested = true;
            if (state.future != null) {
                state.future.cancel(true);
            }
        });
        workers.shutdownNow();
        cleanup.shutdownNow();
    }

    private record ValidatedRead(
            String address,
            int timeoutMs,
            RegisterDataType dataType,
            int decodeIndex,
            RegisterByteOrder byteOrder,
            RegisterWordOrder wordOrder) {
    }

    private static final class SessionState {
        private final UUID id;
        private final Instant startedAt = Instant.now();
        private final int intervalMs;
        private final int durationSeconds;
        private final List<ReadResult> samples = new CopyOnWriteArrayList<>();
        private volatile SessionStatus status = SessionStatus.RUNNING;
        private volatile Instant finishedAt;
        private volatile boolean stopRequested;
        private volatile boolean taskStarted;
        private volatile Future<?> future;

        private SessionState(UUID id, int intervalMs, int durationSeconds) {
            this.id = id;
            this.intervalMs = intervalMs;
            this.durationSeconds = durationSeconds;
        }

        private SessionView view() {
            return new SessionView(id, status, startedAt, finishedAt, intervalMs,
                    durationSeconds, new ArrayList<>(samples));
        }
    }
}

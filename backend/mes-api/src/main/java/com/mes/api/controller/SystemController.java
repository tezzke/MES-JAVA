package com.mes.api.controller;

import com.mes.api.realtime.RealtimeWebSocketHandler;
import com.mes.api.service.ReadinessService;
import com.mes.core.contract.SnapshotQueue;
import com.mes.core.options.AcquisitionOptions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统信息接口:运行模式、健康检查。
 */
@RestController
@RequestMapping("/api/system")
@Tag(name = "系统", description = "运行信息与健康检查")
public class SystemController {

    private final AcquisitionOptions options;
    private final SnapshotQueue queue;
    private final RealtimeWebSocketHandler realtimeHandler;
    private final ReadinessService readinessService;
    private final String version;

    public SystemController(AcquisitionOptions options,
                            SnapshotQueue queue,
                            RealtimeWebSocketHandler realtimeHandler,
                            ReadinessService readinessService,
                            @Value("${mes.version:1.0.0}") String version) {
        this.options = options;
        this.queue = queue;
        this.realtimeHandler = realtimeHandler;
        this.readinessService = readinessService;
        this.version = version;
    }

    /** 系统运行信息:采集模式、设备数量、服务器时间。前端顶栏展示。 */
    @GetMapping("/info")
    @Operation(summary = "系统运行信息")
    public Map<String, Object> getInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("mode", options.getMode());
        info.put("simulation", options.isSimulation());
        info.put("deviceCount", options.enabledDevices().size());
        info.put("serverTime", Instant.now());
        info.put("version", version);
        // 运行时观测指标:队列积压与丢帧数是判断存储瓶颈最直接的信号
        info.put("queueSize", queue.size());
        info.put("droppedFrames", queue.droppedCount());
        info.put("realtimeClients", realtimeHandler.clientCount());
        return info;
    }

    /** 健康检查(部署探活用)。 */
    @GetMapping("/health")
    @Operation(summary = "健康检查")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    /** 就绪检查：两套持久化数据库都可查询时才允许接收流量。 */
    @GetMapping("/readiness")
    @Operation(summary = "数据库就绪检查")
    public ResponseEntity<Map<String, Object>> readiness() {
        ReadinessService.Result result = readinessService.check();
        return ResponseEntity.status(result.ready() ? 200 : 503).body(result.asResponse());
    }
}

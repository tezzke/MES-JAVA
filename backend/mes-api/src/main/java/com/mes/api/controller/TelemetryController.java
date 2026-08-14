package com.mes.api.controller;

import com.mes.api.support.QueryParams;
import com.mes.core.contract.TelemetryRepository;
import com.mes.core.entity.TelemetryRecord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 遥测历史接口:历史曲线查询(数据库)。
 */
@RestController
@RequestMapping("/api/telemetry")
@Tag(name = "遥测", description = "历史曲线查询")
public class TelemetryController {

    private final TelemetryRepository repository;

    public TelemetryController(TelemetryRepository repository) {
        this.repository = repository;
    }

    /**
     * 查询某设备某点位的历史曲线。
     *
     * @param deviceId  设备编码,如 CNC-01。
     * @param pointName 点位编码,如 SpindleTemp。
     * @param from      起始时间(ISO8601,不传默认最近 1 小时)。
     * @param to        结束时间(不传默认当前)。
     * @param maxPoints 返回点数上限(默认 2000,超过自动等距抽稀)。
     */
    @GetMapping("/history")
    @Operation(summary = "历史曲线", description = "数据量超过 maxPoints 时由数据库端等距抽稀")
    public List<TelemetryRecord> getHistory(
            @RequestParam String deviceId,
            @RequestParam String pointName,
            @Parameter(description = "起始时间,ISO8601") @RequestParam(required = false) String from,
            @Parameter(description = "结束时间,ISO8601") @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "2000") int maxPoints) {

        if (deviceId == null || deviceId.isBlank() || pointName == null || pointName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deviceId 与 pointName 为必填参数");
        }

        Instant toUtc = QueryParams.parseInstant(to, Instant.now());
        Instant fromUtc = QueryParams.parseInstant(from, toUtc.minus(Duration.ofHours(1)));
        int limit = QueryParams.clamp(maxPoints, 10, 10_000);

        return repository.query(deviceId, pointName, fromUtc, toUtc, limit);
    }
}

package com.mes.api.controller;

import com.mes.api.support.QueryParams;
import com.mes.core.contract.AlarmRepository;
import com.mes.core.contract.PagedResult;
import com.mes.core.entity.AlarmRecord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 报警接口:激活报警 + 历史报警分页查询。
 */
@RestController
@RequestMapping("/api/alarms")
@Tag(name = "报警", description = "激活报警与历史报警")
public class AlarmController {

    private final AlarmRepository repository;

    public AlarmController(AlarmRepository repository) {
        this.repository = repository;
    }

    /** 获取当前所有未恢复的激活报警。 */
    @GetMapping("/active")
    @Operation(summary = "当前激活报警")
    public List<AlarmRecord> getActive() {
        return repository.findActive();
    }

    /**
     * 分页查询历史报警。
     *
     * @param deviceId 按设备过滤,可空。
     * @param from     起始时间(默认最近 24 小时)。
     * @param to       结束时间(默认当前)。
     * @param page     页码(1 起)。
     * @param pageSize 每页条数(默认 20,最大 200)。
     */
    @GetMapping
    @Operation(summary = "历史报警分页查询")
    public PagedResult<AlarmRecord> query(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        Instant toUtc = QueryParams.parseInstant(to, Instant.now());
        Instant fromUtc = QueryParams.parseInstant(from, toUtc.minus(Duration.ofHours(24)));

        return repository.query(deviceId, fromUtc, toUtc,
                Math.max(1, page), QueryParams.clamp(pageSize, 1, 200));
    }
}

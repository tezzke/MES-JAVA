package com.mes.api.controller;

import com.mes.api.support.QueryParams;
import com.mes.core.contract.BarcodeRepository;
import com.mes.core.contract.PagedResult;
import com.mes.core.entity.BarcodeRecord;
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
 * 扫码追溯接口:分页查询扫码记录。
 */
@RestController
@RequestMapping("/api/barcodes")
@Tag(name = "扫码追溯", description = "条码记录查询")
public class BarcodeController {

    private final BarcodeRepository repository;

    public BarcodeController(BarcodeRepository repository) {
        this.repository = repository;
    }

    /**
     * 分页查询扫码记录,支持条码模糊匹配追溯。
     *
     * @param keyword  条码关键字(模糊匹配),可空。
     * @param deviceId 按绑定设备过滤,可空。
     * @param from     起始时间(默认最近 24 小时)。
     * @param to       结束时间(默认当前)。
     * @param page     页码(1 起)。
     * @param pageSize 每页条数(默认 20,最大 200)。
     */
    @GetMapping
    @Operation(summary = "扫码记录分页查询")
    public PagedResult<BarcodeRecord> query(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        Instant toUtc = QueryParams.parseInstant(to, Instant.now());
        Instant fromUtc = QueryParams.parseInstant(from, toUtc.minus(Duration.ofHours(24)));

        return repository.query(keyword, deviceId, fromUtc, toUtc,
                Math.max(1, page), QueryParams.clamp(pageSize, 1, 200));
    }

    /** 最近扫码记录:前端"实时扫码"列表的初始数据,之后由 WebSocket 增量推送。 */
    @GetMapping("/recent")
    @Operation(summary = "最近扫码记录")
    public List<BarcodeRecord> recent(@RequestParam(defaultValue = "20") int limit) {
        return repository.findRecent(QueryParams.clamp(limit, 1, 100));
    }
}

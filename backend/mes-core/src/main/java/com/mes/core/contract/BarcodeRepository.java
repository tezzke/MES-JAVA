package com.mes.core.contract;

import com.mes.core.entity.BarcodeRecord;

import java.time.Instant;
import java.util.List;

/**
 * 扫码记录仓储。实现:{@code com.mes.infrastructure.repository.JdbcBarcodeRepository}。
 */
public interface BarcodeRepository {

    /** 插入一条扫码记录。 */
    void insert(BarcodeRecord record);

    /** 分页查询扫码记录(条码模糊匹配,用于追溯)。 */
    PagedResult<BarcodeRecord> query(String barcodeKeyword, String deviceId,
                                     Instant fromUtc, Instant toUtc, int page, int pageSize);

    /** 查询最近若干条扫码记录(前端"实时扫码"列表的初始数据)。 */
    List<BarcodeRecord> findRecent(int limit);
}

package com.mes.core.contract;

import java.util.List;

/**
 * 分页查询结果:总条数 + 当前页数据。
 * 与前端 {@code PagedResult<T>} 类型对应,序列化为 {"total": n, "items": [...]}。
 *
 * @param total 满足条件的总记录数。
 * @param items 当前页记录。
 */
public record PagedResult<T>(int total, List<T> items) {

    /** 空结果。 */
    public static <T> PagedResult<T> empty() {
        return new PagedResult<>(0, List.of());
    }
}

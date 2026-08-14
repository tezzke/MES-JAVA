package com.mes.system.domain;

import java.util.List;

/** 系统域统一分页结果。 */
public record PageResult<T>(long total, List<T> items) {
    public PageResult {
        items = List.copyOf(items);
    }
}

package com.mes.api.support;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 查询参数解析工具:统一时间解析与数值夹取,避免每个控制器各写一遍。
 */
public final class QueryParams {

    private QueryParams() {
    }

    /**
     * 宽松解析时间参数,支持三种常见写法:
     * <ul>
     *   <li>{@code 2026-08-12T03:20:00Z} / 带时区偏移(前端 toISOString 的输出)</li>
     *   <li>{@code 2026-08-12T11:20:00}(无时区,按服务器本地时区理解)</li>
     *   <li>毫秒时间戳</li>
     * </ul>
     *
     * @param text     原始参数,null/空白时返回 fallback。
     * @param fallback 缺省值。
     * @return UTC 时刻。
     */
    public static Instant parseInstant(String text, Instant fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        String value = text.trim();

        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            // 继续尝试其他格式
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (Exception ignored) {
            // 继续尝试其他格式
        }
        try {
            return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception ignored) {
            // 继续尝试其他格式
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(value));
        } catch (Exception ignored) {
            // 参数是调用方给错的,回 400 而不是 500
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法解析时间参数:" + text);
        }
    }

    /** 把数值夹取到 [min, max] 区间内。 */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

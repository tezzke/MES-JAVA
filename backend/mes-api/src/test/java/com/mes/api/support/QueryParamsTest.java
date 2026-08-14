package com.mes.api.support;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 查询参数解析测试:前端 toISOString()、手工拼的本地时间、时间戳三种写法都要能查到数据,
 * 时间参数解析错误会直接表现为"曲线查不到数据",而且很难排查。
 */
class QueryParamsTest {

    private static final Instant FALLBACK = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void parsesUtcIsoFromBrowser() {
        assertThat(QueryParams.parseInstant("2026-08-12T03:20:00Z", FALLBACK))
                .isEqualTo(Instant.parse("2026-08-12T03:20:00Z"));
    }

    @Test
    void parsesIsoWithOffset() {
        assertThat(QueryParams.parseInstant("2026-08-12T11:20:00+08:00", FALLBACK))
                .isEqualTo(Instant.parse("2026-08-12T03:20:00Z"));
    }

    @Test
    void parsesLocalDateTimeUsingServerZone() {
        LocalDateTime local = LocalDateTime.of(2026, 8, 12, 11, 20);

        assertThat(QueryParams.parseInstant("2026-08-12T11:20:00", FALLBACK))
                .isEqualTo(local.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    void parsesEpochMillis() {
        Instant expected = Instant.parse("2026-08-12T03:20:00Z");

        assertThat(QueryParams.parseInstant(String.valueOf(expected.toEpochMilli()), FALLBACK))
                .isEqualTo(expected);
    }

    @Test
    void fallsBackWhenParameterMissing() {
        assertThat(QueryParams.parseInstant(null, FALLBACK)).isEqualTo(FALLBACK);
        assertThat(QueryParams.parseInstant("  ", FALLBACK)).isEqualTo(FALLBACK);
    }

    @Test
    void rejectsUnparseableValueWithBadRequest() {
        // 参数是调用方给错的,应回 400 而不是 500
        assertThatThrownBy(() -> QueryParams.parseInstant("昨天", FALLBACK))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void clampKeepsValueInsideRange() {
        assertThat(QueryParams.clamp(5, 10, 100)).isEqualTo(10);
        assertThat(QueryParams.clamp(500, 10, 100)).isEqualTo(100);
        assertThat(QueryParams.clamp(50, 10, 100)).isEqualTo(50);
    }
}

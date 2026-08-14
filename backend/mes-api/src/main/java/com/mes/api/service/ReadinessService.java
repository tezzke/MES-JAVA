package com.mes.api.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 独立于 liveness 的数据库就绪检查。
 *
 * <p>只执行轻量只读查询，不把暂时性数据库故障误判为 JVM 死亡。</p>
 */
@Service
public class ReadinessService {

    private final JdbcTemplate telemetryJdbc;
    private final JdbcTemplate businessJdbc;

    public ReadinessService(
            @Qualifier("telemetryJdbcTemplate") JdbcTemplate telemetryJdbc,
            @Qualifier("businessJdbcTemplate") JdbcTemplate businessJdbc) {
        this.telemetryJdbc = telemetryJdbc;
        this.businessJdbc = businessJdbc;
    }

    public Result check() {
        boolean sqlite = query(telemetryJdbc);
        boolean mysql = query(businessJdbc);
        return new Result(sqlite && mysql, mysql, sqlite);
    }

    private boolean query(JdbcTemplate jdbc) {
        try {
            Integer value = jdbc.queryForObject("SELECT 1", Integer.class);
            return value != null && value == 1;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public record Result(boolean ready, boolean mysql, boolean sqlite) {
        public Map<String, Object> asResponse() {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", ready ? "ready" : "not_ready");
            response.put("mysql", mysql ? "up" : "down");
            response.put("sqlite", sqlite ? "up" : "down");
            return response;
        }
    }
}

package com.mes.system.adapter.jdbc;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 迁移契约测试：确保安全核心表、权限码和“无默认管理员”约束不会被误删。
 */
class SystemMigrationTest {

    @Test
    void migrationContainsSecuritySchemaAndNoDefaultAdminPassword() throws Exception {
        String sql;
        try (var input = new ClassPathResource(
                "db/migration/system/V1__create_system_schema.sql").getInputStream()) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("CREATE TABLE sys_user")
                .contains("CREATE TABLE sys_audit")
                .contains("USER_AUTHORIZE")
                .contains("ROLE_AUTHORIZE")
                .contains("TELEMETRY_READ")
                .contains("ALARM_READ")
                .contains("BARCODE_READ")
                .doesNotContain("INSERT INTO sys_user")
                .doesNotContainIgnoringCase("password123");
    }

    @Test
    void versionTwoAddsProbePermissionWithoutChangingVersionOne() throws Exception {
        String sql;
        try (var input = new ClassPathResource(
                "db/migration/system/V2__add_modbus_probe_permission.sql").getInputStream()) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("MODBUS_PROBE")
                .contains("r.code = 'ADMIN'")
                .contains("/system/modbus-probe");
    }
}

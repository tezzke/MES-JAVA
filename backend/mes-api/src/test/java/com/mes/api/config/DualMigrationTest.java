package com.mes.api.config;

import com.mes.production.adapter.jdbc.ProductionMigrationConfiguration;
import com.mes.system.adapter.jdbc.BusinessDataSourceConfiguration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证同一业务 Schema 中两套独立 Flyway 历史表都会执行各自迁移。
 */
class DualMigrationTest {

    @Test
    void runsSystemAndProductionMigrations() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:dual-migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        var systemResult = new BusinessDataSourceConfiguration().systemFlyway(dataSource).migrate();
        assertEquals(2, systemResult.migrationsExecuted);
        new ProductionMigrationConfiguration().productionFlyway(dataSource).migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name='sys_user'",
                Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name='prod_work_order'",
                Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_name='flyway_schema_history_production'", Integer.class));
    }
}

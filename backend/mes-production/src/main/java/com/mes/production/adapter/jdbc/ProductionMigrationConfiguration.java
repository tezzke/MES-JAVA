package com.mes.production.adapter.jdbc;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.sql.DataSource;

/**
 * 生产域独立 Flyway 迁移器，与系统域使用不同位置和历史表。
 */
@Configuration
public class ProductionMigrationConfiguration {

    @Bean(initMethod = "migrate", name = "productionFlyway")
    @DependsOn("systemFlyway")
    @ConditionalOnProperty(name = "mes.datasource.business.enabled", havingValue = "true")
    public Flyway productionFlyway(@Qualifier("businessDataSource") DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/production")
                .table("flyway_schema_history_production")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
    }
}

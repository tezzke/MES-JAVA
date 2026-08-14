package com.mes.system.adapter.jdbc;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * MySQL 业务库的具名数据源与迁移配置。
 */
@Configuration
public class BusinessDataSourceConfiguration {

    @Bean(name = "businessDataSource")
    @ConfigurationProperties("mes.datasource.business")
    public HikariDataSource businessDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean(name = "businessJdbcTemplate")
    public JdbcTemplate businessJdbcTemplate(
            @Qualifier("businessDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "businessTransactionManager")
    public PlatformTransactionManager businessTransactionManager(
            @Qualifier("businessDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /**
     * 仅在明确启用业务库时迁移，保证离线开发环境无需 MySQL 也能编译和测试。
     */
    @Bean(initMethod = "migrate")
    @ConditionalOnProperty(name = "mes.datasource.business.enabled", havingValue = "true")
    public Flyway systemFlyway(@Qualifier("businessDataSource") DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/system")
                .table("flyway_schema_history_system")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
    }
}

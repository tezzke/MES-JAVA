package com.mes.production.adapter.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mes.production.application.ProductionService;
import com.mes.production.domain.MasterData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 任务生成、幂等键和乐观锁 JDBC 测试。 */
class JdbcProductionRepositoryTest {
    private JdbcProductionRepository repository;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:production;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("""
                CREATE TABLE md_master(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,type VARCHAR(32),code VARCHAR(64),
                  name VARCHAR(120),reference_id BIGINT,secondary_reference_id BIGINT,
                  sequence_no INT,quantity DECIMAL(18,4),attributes_json VARCHAR(2000),
                  enabled BOOLEAN,version BIGINT)""");
        jdbc.execute("""
                CREATE TABLE prod_task(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,work_order_id BIGINT,route_step_id BIGINT,
                  operation_id BIGINT,station_id BIGINT,sequence_no INT,
                  planned_quantity DECIMAL(18,4),good_quantity DECIMAL(18,4),
                  bad_quantity DECIMAL(18,4),status VARCHAR(24),version BIGINT,
                  UNIQUE(work_order_id,route_step_id))""");
        jdbc.execute("""
                CREATE TABLE prod_idempotency(
                  operation_code VARCHAR(40),idempotency_key VARCHAR(100),result_id BIGINT,
                  created_at TIMESTAMP,PRIMARY KEY(operation_code,idempotency_key))""");
        repository = new JdbcProductionRepository(jdbc, new ObjectMapper());
    }

    @Test
    void generatesTasksFromRouteSteps() {
        long step = repository.saveMasterData(new MasterData.Item(null,
                MasterData.Type.ROUTE_STEP, "S10", "装配", 7L, 9L, 10, null,
                Map.of("stationId", "11"), true, 0));
        assertEquals(1, repository.generateTasks(5, 7, new BigDecimal("20")));
        assertEquals(step, repository.findTasks(5).getFirst().routeStepId());
        assertEquals(11L, repository.findTasks(5).getFirst().stationId());
    }

    @Test
    void rejectsDuplicateIdempotencyKey() {
        assertTrue(repository.claimIdempotency("TASK_REPORT", "request-1"));
        assertFalse(repository.claimIdempotency("TASK_REPORT", "request-1"));
        repository.completeIdempotency("TASK_REPORT", "request-1", 88);
        assertEquals(88L, repository.findIdempotentResult("TASK_REPORT", "request-1").orElseThrow());
    }

    @Test
    void rejectsStaleMasterDataVersion() {
        long id = repository.saveMasterData(new MasterData.Item(null, MasterData.Type.OPERATION,
                "OP10", "装配", null, null, null, null, Map.of(), true, 0));
        assertThrows(ProductionService.OptimisticLockException.class,
                () -> repository.saveMasterData(new MasterData.Item(id, MasterData.Type.OPERATION,
                        "OP10", "更新", null, null, null, null, Map.of(), true, 9)));
    }
}


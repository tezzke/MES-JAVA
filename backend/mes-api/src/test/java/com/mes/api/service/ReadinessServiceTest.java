package com.mes.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadinessServiceTest {

    private final JdbcTemplate sqlite = mock(JdbcTemplate.class);
    private final JdbcTemplate mysql = mock(JdbcTemplate.class);

    @Test
    void readyOnlyWhenBothDatabasesRespond() {
        when(sqlite.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(mysql.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        ReadinessService.Result result = new ReadinessService(sqlite, mysql).check();

        assertTrue(result.ready());
        assertTrue(result.sqlite());
        assertTrue(result.mysql());
    }

    @Test
    void notReadyWhenEitherDatabaseFails() {
        when(sqlite.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(mysql.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new IllegalStateException("unavailable"));

        ReadinessService.Result result = new ReadinessService(sqlite, mysql).check();

        assertFalse(result.ready());
        assertTrue(result.sqlite());
        assertFalse(result.mysql());
    }
}

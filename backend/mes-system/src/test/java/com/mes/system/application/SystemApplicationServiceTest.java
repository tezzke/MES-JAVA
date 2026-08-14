package com.mes.system.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mes.system.contract.SystemRepository;
import com.mes.system.domain.SystemUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");

    @Test
    void fifthFailureLocksAccountForFifteenMinutes() {
        SystemRepository repository = mock(SystemRepository.class);
        when(repository.findUserByUsername("operator")).thenReturn(Optional.of(
                new SystemUser(7L, "operator", "操作员", "$2a$12$hash", true,
                        4, null, Set.of(), Set.of())));
        SystemApplicationService service = new SystemApplicationService(repository,
                new BCryptPasswordEncoder(), Clock.fixed(NOW, ZoneOffset.UTC));

        service.loginFailed("operator");

        verify(repository).recordLoginFailure("operator", 5, NOW.plusSeconds(15 * 60));
    }

    @Test
    void earlyFailureDoesNotLockAccount() {
        SystemRepository repository = mock(SystemRepository.class);
        when(repository.findUserByUsername("operator")).thenReturn(Optional.of(
                new SystemUser(7L, "operator", "操作员", "$2a$12$hash", true,
                        1, null, Set.of(), Set.of())));
        SystemApplicationService service = new SystemApplicationService(repository,
                new BCryptPasswordEncoder(), Clock.fixed(NOW, ZoneOffset.UTC));

        service.loginFailed("operator");

        verify(repository).recordLoginFailure(eq("operator"), eq(2), isNull());
    }

    @Test
    void publicUserViewNeverSerializesPasswordHash() throws Exception {
        SystemUser user = new SystemUser(1L, "admin", "管理员", "secret-hash",
                true, 0, null, Set.of("ADMIN"), Set.of("SYSTEM_ADMIN"));

        String json = new ObjectMapper().writeValueAsString(user.toView());

        assertThat(json).doesNotContain("secret-hash", "password", "failedAttempts", "lockedUntil");
    }
}

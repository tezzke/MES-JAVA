package com.mes.system.application;

import com.mes.system.contract.SystemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 一次性管理员引导。仅环境变量存在且库内没有 admin 时创建，绝不输出明文密码。
 */
@Component
@ConditionalOnProperty(name = "mes.datasource.business.enabled", havingValue = "true")
public class BootstrapAdminInitializer {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final SystemRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapPassword;

    public BootstrapAdminInitializer(SystemRepository repository, PasswordEncoder passwordEncoder,
                                     @Value("${MES_BOOTSTRAP_ADMIN_PASSWORD:}") String bootstrapPassword) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapPassword = bootstrapPassword;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional("businessTransactionManager")
    public void initialize() {
        if (bootstrapPassword.isBlank() || repository.findUserByUsername("admin").isPresent()) {
            return;
        }
        long userId = repository.saveUser(null, "admin", "系统管理员",
                passwordEncoder.encode(bootstrapPassword), true);
        repository.findRoles().stream()
                .filter(role -> "ADMIN".equals(role.code()))
                .findFirst()
                .ifPresent(role -> repository.setUserRoles(userId, Set.of(role.id())));
        log.warn("已通过 MES_BOOTSTRAP_ADMIN_PASSWORD 创建初始管理员，请登录后立即轮换密码");
    }
}

package com.mes.system.adapter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mes.system.application.SystemApplicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

/**
 * 服务端会话安全配置：HttpOnly 会话、可读 CSRF Cookie、权限码和安全响应头。
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(SystemApplicationService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setHideUserNotFoundExceptions(true);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
                                                   Environment environment,
                                                   @Qualifier("corsConfigurationSource")
                                                   CorsConfigurationSource corsSource,
                                                   @Value("${server.servlet.session.cookie.secure:false}")
                                                   boolean secureCookie) throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath("/");
        csrf.setCookieCustomizer(cookie -> cookie.secure(secureCookie).sameSite("Strict"));
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName("_csrf");

        http
                .cors(cors -> cors.configurationSource(corsSource))
                .csrf(config -> config.csrfTokenRepository(csrf)
                        .csrfTokenRequestHandler(csrfHandler))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.migrateSession())
                        .maximumSessions(1))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/api/auth/login", "/api/auth/csrf",
                                    "/api/system/health", "/api/system/readiness", "/", "/index.html",
                                    "/assets/**", "/favicon.ico").permitAll();
                    if (environment.acceptsProfiles(Profiles.of("prod"))) {
                        auth.requestMatchers("/swagger/**", "/v3/api-docs/**").hasAuthority("SYSTEM_ADMIN");
                    } else {
                        auth.requestMatchers("/swagger/**", "/v3/api-docs/**").permitAll();
                    }
                    // 厂房模型与设备档案同属 3D 车间的只读骨架数据,共用遥测读权限。
                    auth.requestMatchers("/api/telemetry/**", "/api/devices/**", "/api/plant/**")
                            .hasAuthority("TELEMETRY_READ");
                    auth.requestMatchers("/api/alarms/**").hasAuthority("ALARM_READ");
                    auth.requestMatchers("/api/barcodes/**").hasAuthority("BARCODE_READ");
                    auth.requestMatchers("/api/modbus-probe/**").hasAuthority("MODBUS_PROBE");
                    // WebSocket 在升级后由处理器校验会话并返回可识别的 4401 关闭码。
                    auth.requestMatchers("/hubs/**").permitAll();
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(),
                                    Map.of("code", "UNAUTHORIZED", "message", "需要登录",
                                            "correlationId", correlationId(request)));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(),
                                    Map.of("code", "FORBIDDEN", "message", "权限不足或 CSRF 校验失败",
                                            "correlationId", correlationId(request)));
                        }))
                .headers(headers -> headers
                        .contentTypeOptions(options -> { })
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(policy -> policy.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicy(policy -> policy.policy(
                                "camera=(), microphone=(), geolocation=()")));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${mes.security.allowed-origins:" + AllowedOrigins.DEFAULT + "}")
            String origins) {
        CorsConfiguration configuration = new CorsConfiguration();
        AllowedOrigins.apply(configuration, origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "X-Correlation-ID"));
        configuration.setExposedHeaders(List.of("X-Correlation-ID"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static String correlationId(jakarta.servlet.http.HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}

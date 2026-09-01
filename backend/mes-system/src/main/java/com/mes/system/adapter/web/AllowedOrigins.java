package com.mes.system.adapter.web;

import org.springframework.web.cors.CorsConfiguration;

import java.util.Arrays;
import java.util.List;

/**
 * 解析 {@code mes.security.allowed-origins}。精确 Origin 与含 {@code *} 的模式都可以写在同一项里。
 */
public final class AllowedOrigins {

    /**
     * 开发默认：本机 Vite 以及 RFC1918 内网任意端口。
     * 生产必须用 {@code MES_ALLOWED_ORIGINS} 覆盖为浏览器地址栏的精确 Origin。
     */
    public static final String DEFAULT = "http://localhost:5173,http://127.0.0.1:5173,"
            + "http://192.168.*.*:*,http://10.*.*.*:*,http://172.*.*.*:*";

    private AllowedOrigins() {
    }

    public static List<String> parse(String origins) {
        return Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    public static void apply(CorsConfiguration configuration, String origins) {
        configuration.setAllowedOriginPatterns(parse(origins));
    }
}

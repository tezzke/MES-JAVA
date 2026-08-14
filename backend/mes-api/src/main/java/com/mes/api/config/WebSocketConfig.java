package com.mes.api.config;

import com.mes.api.realtime.RealtimeWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;
import java.util.List;

/**
 * 实时推送端点注册。
 * 路径沿用 .NET 版的 /hubs/realtime,前端与 Vite 代理配置无需任何改动。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    /** 实时通道路径。 */
    public static final String REALTIME_PATH = "/hubs/realtime";

    private final RealtimeWebSocketHandler handler;
    private final List<String> allowedOrigins;

    public WebSocketConfig(RealtimeWebSocketHandler handler,
                           @Value("${mes.security.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
                           String allowedOrigins) {
        this.handler = handler;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(value -> !value.isBlank()).toList();
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, REALTIME_PATH)
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }
}

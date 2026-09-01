package com.mes.api.config;

import com.mes.api.realtime.RealtimeWebSocketHandler;
import com.mes.system.adapter.web.AllowedOrigins;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

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
                           @Value("${mes.security.allowed-origins:" + AllowedOrigins.DEFAULT + "}")
                           String allowedOrigins) {
        this.handler = handler;
        this.allowedOrigins = AllowedOrigins.parse(allowedOrigins);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, REALTIME_PATH)
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
    }
}

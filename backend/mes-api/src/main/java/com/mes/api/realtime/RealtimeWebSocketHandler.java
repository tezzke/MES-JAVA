package com.mes.api.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 实时推送通道(WebSocket,对应 .NET 版的 SignalR Hub)。
 * <p>
 * 前端连接 {@code /hubs/realtime} 后即收到三类事件消息:
 * <ul>
 *   <li>OnSnapshots:设备快照(每个采集节拍推送)</li>
 *   <li>OnAlarm:报警触发/恢复</li>
 *   <li>OnBarcode:扫码记录</li>
 * </ul>
 * 消息统一封装为 {@code {"event":"OnSnapshots","data":...}},前端按 event 分发。
 * 本系统只做服务端 → 客户端的单向广播,因此不处理客户端消息。
 */
@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RealtimeWebSocketHandler.class);
    private static final CloseStatus AUTHENTICATION_REQUIRED =
            new CloseStatus(4401, "Authentication required");

    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;

    public RealtimeWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (session.getPrincipal() == null || session.getPrincipal().getName().isBlank()) {
            session.close(AUTHENTICATION_REQUIRED);
            return;
        }
        sessions.add(session);
        log.info("实时通道已连接:{}(当前 {} 个客户端)", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("实时通道已断开:{}(当前 {} 个客户端)", session.getId(), sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("实时通道传输异常:{}", exception.getMessage());
        sessions.remove(session);
        closeQuietly(session);
    }

    /**
     * 向所有已连接客户端广播一个事件。
     * 无客户端时直接返回,采集链路不受前端在线情况影响。
     */
    public void broadcast(String event, Object payload) {
        if (sessions.isEmpty()) {
            return;
        }

        String message;
        try {
            message = objectMapper.writeValueAsString(Map.of("event", event, "data", payload));
        } catch (Exception ex) {
            log.warn("实时消息序列化失败(event={}):{}", event, ex.getMessage());
            return;
        }

        TextMessage frame = new TextMessage(message);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                // WebSocketSession 不保证并发安全,按会话串行发送
                synchronized (session) {
                    session.sendMessage(frame);
                }
            } catch (IOException | IllegalStateException ex) {
                log.debug("推送到客户端 {} 失败:{}", session.getId(), ex.getMessage());
                sessions.remove(session);
                closeQuietly(session);
            }
        }
    }

    /** 当前在线的前端客户端数量。 */
    public int clientCount() {
        return sessions.size();
    }

    private static void closeQuietly(WebSocketSession session) {
        try {
            session.close();
        } catch (IOException ignored) {
            // 关闭异常无意义,忽略
        }
    }
}

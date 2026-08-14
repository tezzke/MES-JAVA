package com.mes.api.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 实时通道鉴权边界测试。
 */
class RealtimeWebSocketHandlerTest {

    @Test
    void closesAnonymousConnectionWithAuthenticationCode() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getPrincipal()).thenReturn(null);

        new RealtimeWebSocketHandler(new ObjectMapper()).afterConnectionEstablished(session);

        verify(session).close(argThat((CloseStatus status) -> status.getCode() == 4401));
    }
}

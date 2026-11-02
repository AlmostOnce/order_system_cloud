package com.hbue.ordering.notification.websocket;

import com.hbue.ordering.common.web.security.JwtUserIdentity;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import static org.mockito.Mockito.*;

class PasswordRecoverySocketTest {
    @Test void staleCredentialVersionCannotEstablishOrReceiveNotifications() throws Exception {
        var redis=mock(StringRedisTemplate.class);var users=mock(JwtUserIdentity.class);var session=mock(WebSocketSession.class);
        String uuid="12345678-1234-4234-8234-123456789abc";
        when(redis.hasKey("blacklist:token")).thenReturn(false);
        when(users.resolveCurrentIdentity(uuid,0)).thenThrow(new JwtException("password changed"));
        when(session.getAttributes()).thenReturn(Map.of(NotificationSocketHandler.IDENTITY,
                new NotificationTicketService.SessionIdentity(9L,"token",Instant.now().plusSeconds(60).getEpochSecond(),uuid,0)));
        var sockets=new NotificationSocketHandler(redis,"blacklist:",users);
        sockets.afterConnectionEstablished(session);sockets.notifyUser(9L);
        verify(session).close(CloseStatus.POLICY_VIOLATION);verify(session,never()).sendMessage(any());
    }
}

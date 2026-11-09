package com.hbue.ordering.notification.websocket;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import com.hbue.ordering.common.redis.RedisOperationsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 通知与聊天共享的WebSocket传输入口。单连接复用不同事件类型，串行装饰器限制发送耗时和缓冲。
 * 每次消息处理及推送重新验证凭据，聊天还校验当前订单权限；连接存在不等于永久获得聊天授权。
 */
@Component
public class NotificationSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {
    /** 仅记录故障定位信息，不记录票据、令牌或聊天正文。 */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NotificationSocketHandler.class);
    public static final String IDENTITY = "notificationIdentity";
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final RedisOperationsService redis;
    private final String blacklistPrefix;
    private final com.hbue.ordering.common.web.security.JwtUserIdentity users;
    /** 注入黑名单存储、键前缀及凭据版本校验器；连接与每次事件处理都重新验证登录状态。 */
    public NotificationSocketHandler(RedisOperationsService redis, @Value("${security.jwt.blacklist-key-prefix:auth:access-token:blacklist:}") String prefix, com.hbue.ordering.common.web.security.JwtUserIdentity users) {
        this.redis = redis; this.blacklistPrefix = prefix; this.users=users;
    }
    /**
     * 声明客户端和服务端共同使用的通知协议版本，票据子协议只在握手中消费，不作为响应协议回显。
     */
    @Override public List<String> getSubProtocols() { return List.of("notification.v1"); }
    /**
     * 验证握手绑定的身份，设置输入帧上限，并用并发装饰器保存连接，统一串行发送。
     */
    @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object identity = session.getAttributes().get(IDENTITY);
        if (!(identity instanceof NotificationTicketService.SessionIdentity) || !valid((NotificationTicketService.SessionIdentity) identity)) {
            session.close(CloseStatus.POLICY_VIOLATION); return;
        }
        session.setTextMessageSizeLimit(8192);
        // ACK和后台通知共享同一个装饰器，不能直接并发向原始session写数据。
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, 3000, 16384));
    }
    /**
     * 从本实例在线连接表中删除已关闭连接，避免继续推送及内存泄漏。
     */
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { sessions.remove(session.getId()); }
    /**
     * 清理传输异常的连接并关闭底层Socket。客户端通过已有退避机制重连和补查，不依赖旧连接恢复。
     */
    @Override public void handleTransportError(WebSocketSession session, Throwable error) throws Exception { sessions.remove(session.getId()); if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR); }
    /**
     * 仅向指定通知接收人的有效在线设备发送notification.changed信号，正文仍通过本人HTTP列表读取。
     */
    public void notifyUser(long userId) {
        for (WebSocketSession session : sessions.values()) {
            Object value = session.getAttributes().get(IDENTITY);
            if (!(value instanceof NotificationTicketService.SessionIdentity identity) || identity.userId() != userId) continue;
            if (!valid(identity)) { close(session); continue; }
            try { session.sendMessage(new TextMessage("{\"type\":\"notification.changed\"}")); }
            catch (IOException | RuntimeException error) { close(session); }
        }
    }
    /**
     * 周期性清理过期、注销或凭据版本失效的连接，避免长期连接绕过普通HTTP认证检查。
     */
    @Scheduled(fixedDelay = 30000)
    public void closeExpiredSessions() {
        for (WebSocketSession session : sessions.values()) {
            Object value = session.getAttributes().get(IDENTITY);
            if (!(value instanceof NotificationTicketService.SessionIdentity identity) || !valid(identity)) close(session);
        }
    }
    /**
     * 核验令牌到期时间、注销黑名单、当前用户身份与凭据版本。Redis或身份查询失败时拒绝使用该连接。
     */
    private boolean valid(NotificationTicketService.SessionIdentity identity) {
        if (identity.expiresAt() <= Instant.now().getEpochSecond()) return false;
        try { return Boolean.FALSE.equals(redis.hasKey(blacklistPrefix + identity.tokenId()))
                && Long.valueOf(identity.userId()).equals(users.resolveCurrentIdentity(identity.userUuid(),identity.credentialVersion())); }
        catch (RuntimeException error) { return false; }
    }
    private com.hbue.ordering.notification.service.ChatService chats;
    private com.fasterxml.jackson.databind.ObjectMapper json;
    private boolean chatEnabled;
    /**
     * 注入共享聊天业务与JSON编解码器。通过开关隔离未迁移的聊天功能，不改变现有构造器调用契约。
     */
    @org.springframework.beans.factory.annotation.Autowired
    void configureChat(com.hbue.ordering.notification.service.ChatService chats,
            com.fasterxml.jackson.databind.ObjectMapper json,@Value("${notification.chat.enabled:false}") boolean enabled) {
        this.chats=chats;this.json=json;this.chatEnabled=enabled;
    }
    /**
     * 处理客户端文字帧。验证当前会话身份、帧大小、JSON结构和命令类型；消息落库后返回ACK，失败不伪装成功。
     */
    @Override protected void handleTextMessage(WebSocketSession source, TextMessage message) throws Exception {
        WebSocketSession session=sessions.get(source.getId());
        if(session==null)return;
        var identity=(NotificationTicketService.SessionIdentity)session.getAttributes().get(IDENTITY);
        if(!valid(identity)){close(session);return;}
        String requestId="";
        try {
            if(message.getPayloadLength()>8192)throw new IllegalArgumentException();
            var frame=json.readTree(message.getPayload());
            if(frame==null||!frame.isObject())throw new IllegalArgumentException();
            requestId=frame.path("requestId").asText("");
            if(requestId.length()>64)throw new IllegalArgumentException();
            if("ping".equals(frame.path("type").asText())){reply(session,java.util.Map.of("type","pong"));return;}
            if(!chatEnabled)throw new com.hbue.ordering.common.core.exception.BusinessException(com.hbue.ordering.common.core.error.CommonErrorCode.SERVICE_UNAVAILABLE,"聊天暂未启用");
            if(!"chat.send".equals(frame.path("type").asText()))throw new IllegalArgumentException();
            var command=new com.hbue.ordering.notification.dto.ChatSendRequest(frame.path("conversationUuid").asText(),frame.path("clientMessageId").asText(),frame.path("content").isTextual()?frame.path("content").asText():null);
            // Service事务代理返回后消息已经提交，此时才能向发送者确认持久化成功。
            var saved=chats.send(identity.userId(),command);
            reply(session,java.util.Map.of("type","chat.ack","requestId",requestId,"conversationUuid",command.conversationUuid(),"message",saved));
        } catch(com.hbue.ordering.common.core.exception.BusinessException e) {
            reply(session,java.util.Map.of("type","chat.error","requestId",requestId,"code",e.getErrorCode().code(),"message",e.getMessage()));
        } catch(Exception e) {
            // 非业务异常不能静默吞掉；保留服务端堆栈便于定位，客户端只收到统一提示。
            log.error("聊天消息处理异常，sessionId={}，异常类型={}", session.getId(), e.getClass().getSimpleName(), e);
            reply(session,java.util.Map.of("type","chat.error","requestId",requestId,"code","COMMON_503","message","消息未确认，请稍后重试"));
        }
    }
    /**
     * 经已保存的串行Socket装饰器发送JSON响应，避免ACK与后台通知并发写入同一连接。
     */
    private void reply(WebSocketSession session,Object value) throws java.io.IOException {session.sendMessage(new TextMessage(json.writeValueAsString(value)));}
    /**
     * 对在线连接逐一复核该会话当前权限，仅向授权用户发送chat.changed。无权限或依赖不可用时不发送会话正文。
     */
    public void notifyChat(String uuid) {
        if(!chatEnabled||uuid==null||!uuid.matches("[0-9a-f-]{36}"))return;
        for(var session:sessions.values()) {
            var identity=(NotificationTicketService.SessionIdentity)session.getAttributes().get(IDENTITY);
            if(!valid(identity)){close(session);continue;}
            // 只推轻量信号；每次核验当前权限，不向撤权店主发送任何正文。
            if(!chats.canRead(identity.userId(),uuid))continue;
            try{reply(session,java.util.Map.of("type","chat.changed","conversationUuid",uuid));}
            catch(java.io.IOException|RuntimeException e){close(session);}
        }
    }
    /**
     * 先删除连接表记录再关闭Socket；关闭异常不阻止其他连接继续清理。
     */
    private void close(WebSocketSession session) {
        sessions.remove(session.getId());
        try { if (session.isOpen()) session.close(CloseStatus.POLICY_VIOLATION); }
        catch (IOException ignored) { }
    }
}

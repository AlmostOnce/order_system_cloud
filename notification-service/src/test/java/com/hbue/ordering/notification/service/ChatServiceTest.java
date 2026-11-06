package com.hbue.ordering.notification.service;
import com.hbue.ordering.notification.service.impl.ChatServiceImpl;
import com.hbue.ordering.notification.mapper.*;
import com.hbue.ordering.notification.model.*;
import com.hbue.ordering.notification.dto.ChatSendRequest;
import com.hbue.ordering.order.api.vo.OrderChatContextVO;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
/** 聊天业务边界测试：验证幂等、越权、期限、序号与事务事件写入意图；不连接真实数据库。 */
class ChatServiceTest {
    static final String CHAT="11111111-1111-4111-8111-111111111111", KEY="22222222-2222-4222-8222-222222222222";
    final ChatConversationMapper c=mock(ChatConversationMapper.class);
    final ChatMessageMapper m=mock(ChatMessageMapper.class);
    final ChatOutboxMapper o=mock(ChatOutboxMapper.class);
    final ChatAccessService a=mock(ChatAccessService.class);
    final RedisOperationsService r=mock(RedisOperationsService.class);
    final ChatService service=new ChatServiceImpl(c,m,o,a,r);
    ChatConversationDO conversation(){
        var row=new ChatConversationDO();
        row.setId(1L);
        row.setConversationUuid(CHAT);
        row.setOrderId(8L);
        row.setLastSeq(2L);
        row.setCustomerId(7L);
        return row;
    }
    void sender(){
        when(c.lock(CHAT)).thenReturn(conversation());
        when(a.actor(7)).thenReturn(new ChatAccessService.Actor(7,KEY,"顾客","CUSTOMER",List.of()));
        when(a.order(8)).thenReturn(new OrderChatContextVO(8L,"O8",7L,3L,"店铺","已支付",null));
    }
    @Test void retryReturnsCommittedMessageWithoutWritingOrRateLimit(){
        sender();
        var old=new ChatMessageDO();
        old.setContent("你好");
        old.setSeq(1L);
        old.setMessageUuid(KEY);
        when(m.duplicate(1,7,KEY)).thenReturn(old);
        assertEquals(KEY,service.send(7,new ChatSendRequest(CHAT,KEY,"你好")).messageUuid());
        verify(m,never()).insert(any(ChatMessageDO.class));
        verifyNoInteractions(r,o);
    }
    @Test void reusedKeyCannotChangeContent(){
        sender();
        var old=new ChatMessageDO();
        old.setContent("旧内容");
        when(m.duplicate(1,7,KEY)).thenReturn(old);
        assertThrows(BusinessException.class,()->service.send(7,new ChatSendRequest(CHAT,KEY,"新内容")));
        verifyNoInteractions(r,o);
    }
    @Test void unauthorizedSendDoesNotPersist(){
        sender();
        doThrow(new BusinessException(com.hbue.ordering.common.core.error.CommonErrorCode.NOT_FOUND,"会话不存在")).when(a).check(any(),any());
        assertThrows(BusinessException.class,()->service.send(7,new ChatSendRequest(CHAT,KEY,"你好")));
        verify(m,never()).insert(any(ChatMessageDO.class));
    }
    @Test void expiredConversationIsReadOnly(){
        sender();
        when(a.canSend(any())).thenReturn(false);
        assertThrows(BusinessException.class,()->service.send(7,new ChatSendRequest(CHAT,KEY,"你好")));
        verifyNoInteractions(r,o);
    }
    @Test void successfulSendAdvancesSequenceAndWritesOutbox(){
        sender();
        when(a.canSend(any())).thenReturn(true);
        when(r.increment(anyString(),any(java.time.Duration.class))).thenReturn(1L);
        var saved=service.send(7,new ChatSendRequest(CHAT,KEY,"你好"));
        assertEquals(3,saved.seq());
        verify(c).advance(1,3,"你好");
        verify(o).insert(any(ChatOutboxDO.class));
    }
    @Test void readingBeyondHistoryIsRejected(){
        when(c.find(CHAT)).thenReturn(conversation());
        assertThrows(BusinessException.class,()->service.read(7,CHAT,3));
        verify(m,never()).read(anyLong(),anyLong(),anyLong());
    }
    @Test void initialConversationUsesCurrentReadAfterConcurrentInsert(){
        when(a.actor(7)).thenReturn(new ChatAccessService.Actor(7,KEY,"顾客","CUSTOMER",List.of()));
        when(a.order(8)).thenReturn(new OrderChatContextVO(8L,"O8",7L,3L,"店铺","已支付",null));
        when(a.canSend(any())).thenReturn(true);
        when(c.byOrderCurrent(8)).thenReturn(conversation());
        assertEquals(CHAT,service.open(7,8).conversationUuid());
        verify(c).byOrderCurrent(8);
    }
}

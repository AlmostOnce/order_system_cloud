package com.hbue.ordering.notification.vo;

import com.hbue.ordering.notification.model.ChatMessageDO;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 撤回后不泄露原文，数据库原文仅用于幂等核对，不随查询或重试响应发给客户端。 */
class ChatMessageViewTest {
    @Test void revokedViewHidesOriginalContent() {
        var message = new ChatMessageDO();
        message.setSeq(1L); message.setContent("撤回前私密正文");
        assertEquals("撤回前私密正文", ChatMessageVO.from(message).content());
        var time = LocalDateTime.of(2026,11,9,11,0);
        message.setRevokedAt(time);
        assertEquals("该消息已撤回", ChatMessageVO.from(message).content());
        assertEquals(time, ChatMessageVO.from(message).revokedAt());
        assertEquals("撤回前私密正文", message.getContent());
    }
}

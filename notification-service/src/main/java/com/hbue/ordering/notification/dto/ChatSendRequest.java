package com.hbue.ordering.notification.dto;
/**
 * 客户端发送命令。会话UUID指定资源，clientMessageId标识一次发送意图；正文与身份必须在服务端重新验证。
 * @param conversationUuid 目标会话公开UUID，后端仍需核验访问范围
 * @param clientMessageId 本次文字的UUID幂等键，ACK丢失重试时不得更换
 * @param content 1至500个Unicode码点的非空白纯文字正文
 */
public record ChatSendRequest(String conversationUuid, String clientMessageId, String content) {
}

package com.hbue.ordering.notification.service;

import com.hbue.ordering.notification.dto.ChatSendRequest;
import com.hbue.ordering.notification.vo.ChatConversationVO;
import com.hbue.ordering.notification.vo.ChatMessageVO;
import com.hbue.ordering.notification.vo.ChatPageVO;

/**
 * 聊天应用服务契约。调用方只传认证后的内部用户编号，不能采信客户端发送人、角色或窗口归属。
 */
public interface ChatService {
    /**
     * 显式点击联系商家后获取或创建会话。先验证真实订单与用户归属，再依赖订单唯一索引解决并发；支付本身不触发创建。
     */
    ChatConversationVO open(long userId, long orderId);

    /**
     * 读取授权会话及当前可发送状态。历史只读与是否能继续发送分别判断，订单完成不删除已有聊天。
     */
    ChatConversationVO detail(long userId, String uuid);

    /**
     * 按当前用户可访问范围查询游标页。游标属于其他用户时拒绝；额外取一条判断下一页，避免加载全部记录。
     */
    ChatPageVO<ChatConversationVO> page(long userId, String cursor, int size);

    /**
     * 按seq查询历史或断线后的增量。before和after互斥；首屏倒序取最近消息，增量按升序避免跳过中间缺口。
     */
    ChatPageVO<ChatMessageVO> messages(long userId, String uuid, Long before, Long after, int size);

    /**
     * 在会话行锁保护下发送纯文字。先检查已提交的幂等记录，再检查可发送期限和账号限流；重复重试不再次扣配额或增加seq。
     */
    ChatMessageVO send(long userId, ChatSendRequest command);

    /**
     * 提交本人实际阅读位置。水位不能超过已提交最大序号，SQL用GREATEST保证并发请求不会把已读位置回退。
     */
    void read(long userId, String uuid, long seq);

    /**
     * 查询本人当前可见会话的未读总量。店主无直接窗口授权时返回零，不把平台或餐厅范围展开成私人会话。
     */
    long unread(long userId);

    /**
     * 批量筛选在线推送接收者。
     *
     * <p>每条聊天事件只回源一次会话与订单上下文：顾客按订单顾客编号直接命中，店主才回源角色与直接窗口。
     * 用于替代"按连接逐条 canRead"的写法，避免在线连接数把跨服务调用放大为 N 倍。</p>
     *
     * <p>会话不存在、依赖异常或用户授权失败一律不返回该用户，保持 fail-closed 语义。</p>
     *
     * @param conversationUuid 会话公开标识
     * @param userIds          当前在线用户编号集合，允许含重复
     * @return 有权限接收该会话变更信号的用户编号
     */
    java.util.Set<Long> readableUsers(String conversationUuid, java.util.Collection<Long> userIds);

    /**
     * 仅本人消息可在十分钟内撤回，重复撤回幂等返回，已读不限制撤回。
     */
    ChatMessageVO revoke(long userId, String conversationUuid, String messageUuid);

    /**
     * 授权后批量补查已加载消息的撤回状态，最多50条，支持较早历史更新。
     */
    java.util.List<ChatMessageVO> revoked(long userId, String conversationUuid, java.util.List<String> messageUuids);
}

package com.hbue.ordering.notification.service;
import com.hbue.ordering.notification.dto.ChatSendRequest;
import com.hbue.ordering.notification.vo.*;
/**
 * 聊天应用服务契约。调用方只传认证后的内部用户编号，不能采信客户端发送人、角色或窗口归属。
 */
public interface ChatService {
    /**
     * 显式点击联系商家后获取或创建会话。先验证真实订单与用户归属，再依赖订单唯一索引解决并发；支付本身不触发创建。
     */
    ChatConversationVO open(long userId,long orderId);
    /**
     * 读取授权会话及当前可发送状态。历史只读与是否能继续发送分别判断，订单完成不删除已有聊天。
     */
    ChatConversationVO detail(long userId,String uuid);
    /**
     * 按当前用户可访问范围查询游标页。游标属于其他用户时拒绝；额外取一条判断下一页，避免加载全部记录。
     */
    ChatPageVO<ChatConversationVO> page(long userId,String cursor,int size);
    /**
     * 按seq查询历史或断线后的增量。before和after互斥；首屏倒序取最近消息，增量按升序避免跳过中间缺口。
     */
    ChatPageVO<ChatMessageVO> messages(long userId,String uuid,Long before,Long after,int size);
    /**
     * 在会话行锁保护下发送纯文字。先检查已提交的幂等记录，再检查可发送期限和账号限流；重复重试不再次扣配额或增加seq。
     */
    ChatMessageVO send(long userId,ChatSendRequest command);
    /**
     * 提交本人实际阅读位置。水位不能超过已提交最大序号，SQL用GREATEST保证并发请求不会把已读位置回退。
     */
    void read(long userId,String uuid,long seq);
    /**
     * 查询本人当前可见会话的未读总量。店主无直接窗口授权时返回零，不把平台或餐厅范围展开成私人会话。
     */
    long unread(long userId);
    /**
     * 供在线推送筛选接收者。资源不存在、授权撤销或依赖异常都返回false，不向该连接泄露聊天内容。
     */
    boolean canRead(long userId,String uuid);
}

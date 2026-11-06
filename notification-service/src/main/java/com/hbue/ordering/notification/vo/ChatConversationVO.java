package com.hbue.ordering.notification.vo;
import java.time.LocalDateTime;
/**
 * 对外会话摘要。仅返回公开会话标识、订单摘要与本人未读数；canSend由服务端查询当前订单状态后决定。
 * @param conversationUuid 对外会话UUID，代替内部会话主键
 * @param orderId 关联订单编号，用于学生端跳转详情
 * @param orderNo 订单业务号快照
 * @param windowName 店铺名称快照，不用窗口数字代替名称
 * @param preview 最近文字消息摘要，空会话为空字符串
 * @param activityAt 最近消息时间；无消息时使用会话创建时间
 * @param lastSeq 会话已提交的最大消息序号
 * @param unreadCount 本人尚未阅读的对方消息数，不包括自己发送的消息
 * @param canSend 详情接口按实时订单状态计算；列表仅用于导航，返回false后进入详情重新确认
 */
public record ChatConversationVO(String conversationUuid, long orderId, String orderNo, String windowName,
String preview, LocalDateTime activityAt, long lastSeq, long unreadCount, boolean canSend) {
}

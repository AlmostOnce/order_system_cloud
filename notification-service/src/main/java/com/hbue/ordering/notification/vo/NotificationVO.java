package com.hbue.ordering.notification.vo;

import java.time.LocalDateTime;

/**
 * 对外系统通知视图。公开通知UUID与阅读时间用于客户端展示，不泄露接收人的内部编号。
 *
 * @param notificationId 公开通知UUID，不是数据库内部主键
 * @param type           通知业务类型
 * @param title          通知标题快照
 * @param content        纯文本通知内容
 * @param orderId        跳转订单详情所需订单编号
 * @param bizType        关联业务类型，SUPPORT 表示反馈工单
 * @param bizId          关联业务公开标识，配合 bizType 跳转反馈详情
 * @param createdAt      服务端创建时间
 * @param readAt         本人首次阅读时间，空表示未读
 */
public record NotificationVO(String notificationId, String type, String title, String content,
                             Long orderId, String bizType, String bizId,
                             LocalDateTime createdAt, LocalDateTime readAt) {
}

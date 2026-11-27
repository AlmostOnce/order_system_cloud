package com.hbue.ordering.support.vo;

import java.time.LocalDateTime;

/**
 * 学生「我的反馈」列表项。
 *
 * <p>只包含列表展示所需字段，描述截断后返回，避免一次返回大段正文与图片。
 * 类型、分类、状态的中文名称同时返回，客户端版本不一致时仍可展示。</p>
 *
 * @param ticketId       公开工单标识
 * @param displayNo      展示编号，取公开标识前 8 位大写
 * @param type           类型编码
 * @param typeLabel      类型中文名称
 * @param categoryLabel  分类中文名称
 * @param status         状态编码
 * @param statusLabel    状态中文名称
 * @param orderNo        关联订单号，可空
 * @param windowName     窗口名称快照，可空
 * @param description    描述摘要，最多 60 字
 * @param customerUnread 是否有商家新回复
 * @param createdAt      提交时间
 * @param lastReplyAt    最近商家回复时间，可空
 * @author order-system
 * @date 2026-11-23
 */
public record SupportTicketSummaryVO(
        String ticketId,
        String displayNo,
        String type,
        String typeLabel,
        String categoryLabel,
        String status,
        String statusLabel,
        String orderNo,
        String windowName,
        String description,
        boolean customerUnread,
        LocalDateTime createdAt,
        LocalDateTime lastReplyAt
) {
}

package com.hbue.ordering.support.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端工单视图。
 *
 * <p>在学生详情基础上额外返回提交人内部编号、餐厅编号和完整时间线（含内部备注），
 * 供具备数据范围的管理角色处理。首期不调用用户服务补昵称，只返回内部编号。</p>
 *
 * @param ticketId       公开工单标识
 * @param displayNo      展示编号
 * @param type           类型编码
 * @param typeLabel      类型中文名称
 * @param category       分类编码
 * @param categoryLabel  分类中文名称
 * @param status         状态编码
 * @param statusLabel    状态中文名称
 * @param customerId     提交学生内部用户编号
 * @param orderId        关联订单编号，可空
 * @param orderNo        关联订单号快照，可空
 * @param windowId       窗口编号，可空
 * @param windowName     窗口名称快照，可空
 * @param restaurantId   所属餐厅编号，可空
 * @param description    问题描述
 * @param imageUrls      描述图片展示地址
 * @param deviceInfo     软件问题设备信息，可空
 * @param customerUnread 学生是否有未读回复
 * @param createdAt      提交时间
 * @param updatedAt      最近更新时间
 * @param lastReplyAt    最近商家公开回复时间，可空
 * @param closedAt       关闭时间，可空
 * @param messages       完整时间线（队列列表返回空列表）
 * @author order-system
 * @date 2026-11-23
 */
public record SupportManagementTicketVO(
        String ticketId,
        String displayNo,
        String type,
        String typeLabel,
        String category,
        String categoryLabel,
        String status,
        String statusLabel,
        Long customerId,
        Long orderId,
        String orderNo,
        Long windowId,
        String windowName,
        Long restaurantId,
        String description,
        List<String> imageUrls,
        String deviceInfo,
        boolean customerUnread,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastReplyAt,
        LocalDateTime closedAt,
        List<SupportTicketMessageVO> messages
) {
}

package com.hbue.ordering.support.vo;

import java.time.LocalDateTime;

/**
 * 工单时间线消息视图。
 *
 * <p>图片以网关下的相对展示地址返回，客户端只按文件键读取，不信任后端以外的
 * 主机地址。内部备注仅出现在管理端详情中，学生接口在 SQL 层已过滤。</p>
 *
 * @param messageId   公开消息标识
 * @param senderType  发送方编码
 * @param senderLabel 发送方中文名称
 * @param content     消息正文
 * @param imageUrls   图片展示地址
 * @param internalNote 是否内部备注
 * @param createdAt   写入时间
 * @author order-system
 * @date 2026-11-23
 */
public record SupportTicketMessageVO(
        String messageId,
        String senderType,
        String senderLabel,
        String content,
        java.util.List<String> imageUrls,
        boolean internalNote,
        LocalDateTime createdAt
) {
}

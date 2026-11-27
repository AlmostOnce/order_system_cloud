package com.hbue.ordering.support.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单时间线消息持久化对象。
 *
 * <p>映射 support_db 中的 {@code support_ticket_message} 表。学生补充、商家公开
 * 回复、内部备注和系统状态消息共用同一时间线，通过 {@link #senderType} 与
 * {@link #internalNote} 区分展示方式。内部备注只能出现在管理端详情中。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@Data
@TableName("support_ticket_message")
public class SupportTicketMessageDO {

    /**
     * 数据库自增主键，时间线按此列正序排序。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 公开消息标识。
     */
    private String messageId;

    /**
     * 关联工单内部主键；接口层先用公开标识定位工单再写入。
     */
    private Long ticketId;

    /**
     * 发送方类型：CUSTOMER / STAFF / SYSTEM。
     */
    private String senderType;

    /**
     * 发送方内部用户编号；系统消息为空。
     */
    private Long senderId;

    /**
     * 消息正文。
     */
    private String content;

    /**
     * 图片文件键 JSON 数组字符串，最多 3 个。
     */
    private String imageFileKeys;

    /**
     * 是否内部备注：1 表示学生接口不可见。
     */
    private Integer internalNote;

    /**
     * 补充说明幂等键；系统消息与公开回复可为空。
     */
    private String requestKey;

    /**
     * 创建时间，由数据库默认值写入。
     */
    private LocalDateTime createdAt;
}

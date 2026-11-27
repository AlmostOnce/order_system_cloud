package com.hbue.ordering.support.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 反馈工单持久化对象。
 *
 * <p>映射 support_db 中的 {@code support_ticket} 表，只承载表字段与 ORM 映射，
 * 不作为接口响应类型。内部主键只用于存储与游标分页，对外一律使用
 * {@link #ticketId}（UUID）。订单号、窗口名与餐厅编号均为提交时的快照，
 * 后续商品或订单数据变化不回溯修改历史工单。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@Data
@TableName("support_ticket")
public class SupportTicketDO {

    /**
     * 数据库自增主键，只用于内部存储和游标排序。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 公开工单标识，详情、补充、关闭接口均按此列查询。
     */
    private String ticketId;

    /**
     * 提交学生的内部用户编号，取自已验证 JWT 的 sub。
     */
    private Long customerId;

    /**
     * 工单类型编码：DINING / SOFTWARE。
     */
    private String type;

    /**
     * 分类编码，写入前经过白名单校验。
     */
    private String category;

    /**
     * 状态编码：PENDING / PROCESSING / CLOSED。
     */
    private String status;

    /**
     * 关联订单编号，仅用餐问题可能非空。
     */
    private Long orderId;

    /**
     * 关联订单号快照，便于学生和管理端直接展示。
     */
    private String orderNo;

    /**
     * 窗口编号；用餐问题必须能确定窗口。
     */
    private Long windowId;

    /**
     * 窗口名称快照，来自订单或商品服务的查询结果。
     */
    private String windowName;

    /**
     * 所属餐厅编号，由商品服务按窗口反查，用于管理端数据范围判定。
     */
    private Long restaurantId;

    /**
     * 问题描述，提交时去除首尾空白并限制长度。
     */
    private String description;

    /**
     * 图片文件键 JSON 数组字符串；接口层解析为数组，数据库不保存图片内容。
     */
    private String imageFileKeys;

    /**
     * 软件问题自动上报的设备信息，最多 255 字。
     */
    private String deviceInfo;

    /**
     * 是否有商家新回复：0 否，1 是。学生查看详情后清零。
     */
    private Integer customerUnread;

    /**
     * 创建幂等键，与学生编号共同构成唯一约束。
     */
    private String requestKey;

    /**
     * 创建内容摘要，用于识别同一幂等键下的不同内容。
     */
    private String requestHash;

    /**
     * 创建时间，由数据库默认值写入。
     */
    private LocalDateTime createdAt;

    /**
     * 最近更新时间，状态变更、补充说明与公开回复都会刷新。
     */
    private LocalDateTime updatedAt;

    /**
     * 最近一次商家公开回复时间；内部备注不更新此字段。
     */
    private LocalDateTime lastReplyAt;

    /**
     * 关闭时间；未关闭时为空。
     */
    private LocalDateTime closedAt;
}

package com.hbue.ordering.support.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 学生提交反馈的请求体。
 *
 * <p>只承载客户端可提交的字段；用户编号取自 JWT，不接受客户端指定。
 * 类型、分类、描述长度、关联对象和图片数量由 Service 按业务规则校验，
 * 校验失败返回可直接展示的中文文案。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@Data
public class CreateSupportTicketRequest {

    /**
     * 工单类型：DINING / SOFTWARE。
     */
    private String type;

    /**
     * 分类编码，必须属于所选类型。
     */
    private String category;

    /**
     * 关联订单编号；用餐问题必填，软件问题忽略。
     */
    private Long orderId;

    /**
     * 兼容旧客户端的窗口编号；不再作为独立关联方式。
     *
     * <p>用餐问题以订单快照中的窗口为准，仅当该值与订单窗口一致时接受，
     * 防止绕过订单伪造关联窗口；软件问题忽略。</p>
     */
    private Long windowId;

    /**
     * 问题描述，去除首尾空白后要求 10-1000 字。
     */
    private String description;

    /**
     * 图片文件键，最多 3 个；每个键都必须由当前学生以 SUPPORT_IMAGE 类型上传。
     */
    @Size(max = 3, message = "最多上传 3 张图片")
    private List<String> imageFileKeys;

    /**
     * 软件问题自动上报的设备信息；用餐问题忽略。
     */
    private String deviceInfo;
}

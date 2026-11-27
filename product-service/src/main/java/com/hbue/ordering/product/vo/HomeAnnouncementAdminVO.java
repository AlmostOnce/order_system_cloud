package com.hbue.ordering.product.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理端首页公告视图。
 *
 * <p>包含状态、生效窗口、发布时间与操作人编号，供公告管理页展示和判断
 * 可用操作（草稿可发布、已发布可下线或编辑）。操作人只返回内部用户编号，
 * 不在此处跨服务查询用户名，避免管理端列表引入额外的用户服务依赖。</p>
 *
 * @author order-system
 * @date 2026-11-26
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HomeAnnouncementAdminVO {

    /**
     * 公告内部编号。
     */
    private Long announcementId;

    /**
     * 公告标题。
     */
    private String title;

    /**
     * 公告正文。
     */
    private String content;

    /**
     * 状态：0 草稿、1 已发布、2 已下线。
     */
    private Integer status;

    /**
     * 生效开始时间。
     */
    private LocalDateTime startAt;

    /**
     * 生效结束时间；为空表示长期有效。
     */
    private LocalDateTime endAt;

    /**
     * 最近一次发布时间；草稿为空。
     */
    private LocalDateTime publishedAt;

    /**
     * 创建人用户编号。
     */
    private Long createdBy;

    /**
     * 最后修改人用户编号。
     */
    private Long updatedBy;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 修改时间。
     */
    private LocalDateTime updatedAt;
}

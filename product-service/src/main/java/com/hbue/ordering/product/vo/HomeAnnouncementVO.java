package com.hbue.ordering.product.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 学生端首页公告视图。
 *
 * <p>只包含展示所需字段，不返回状态、创建人等管理端信息。服务端已经把
 * "是否生效、是否最新"筛选完成，客户端直接渲染即可，不使用本机时间判断过期。</p>
 *
 * @author order-system
 * @date 2026-11-26
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HomeAnnouncementVO {

    /**
     * 公告内部编号，作为前端列表渲染的稳定标识。
     */
    private Long announcementId;

    /**
     * 公告标题，首页公告条展示。
     */
    private String title;

    /**
     * 公告正文，底部弹层展示完整内容。
     */
    private String content;

    /**
     * 最近一次发布时间，弹层展示为"发布时间"。
     */
    private LocalDateTime publishedAt;
}

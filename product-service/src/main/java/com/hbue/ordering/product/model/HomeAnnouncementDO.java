package com.hbue.ordering.product.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生端首页公告持久化对象。
 *
 * <p>映射 product_db 中的 home_announcements 表。公告是平台级内容，不隶属于
 * 某个餐厅；该对象只负责字段映射，是否展示、何时生效、由谁发布等业务规则
 * 分别由 {@code HomeAnnouncementServiceImpl} 和 Controller 的权限门禁负责。</p>
 *
 * <p>状态与时间窗口共同决定可见性：只有"已发布"且落在 [start_at, end_at) 区间内的
 * 公告才会返回给学生端。end_at 为空表示长期有效，直到被手动下线或被下一条公告取代。</p>
 *
 * @author order-system
 * @date 2026-11-26
 */
@Data
@TableName("home_announcements")
public class HomeAnnouncementDO {

    /**
     * 公告内部编号，自增主键；学生端不用于业务判断，仅作为公开标识返回。
     */
    @TableId(value = "announcement_id", type = IdType.AUTO)
    private Long announcementId;

    /**
     * 公告标题，首页公告条与底部弹层展示。
     */
    @TableField("title")
    private String title;

    /**
     * 公告正文，纯文本；学生端列表态单行省略，弹层展示完整内容。
     */
    @TableField("content")
    private String content;

    /**
     * 状态：0 草稿、1 已发布、2 已下线。
     *
     * <p>不直接使用布尔字段，因为"草稿"与"已下线"在管理端需要区分展示：
     * 草稿从未对外发布，已下线则是曾经发布后停止展示。</p>
     */
    @TableField("status")
    private Integer status;

    /**
     * 生效开始时间；服务端按本机时区（Asia/Shanghai）写入与比较。
     */
    @TableField("start_at")
    private LocalDateTime startAt;

    /**
     * 生效结束时间；为空表示长期有效，直到手动下线或被下一条公告取代。
     */
    @TableField("end_at")
    private LocalDateTime endAt;

    /**
     * 最近一次发布时间，用于多条公告同时生效时取最新的一条。
     *
     * <p>草稿尚未发布时为空；编辑已发布公告并保存时会刷新该时间，
     * 使修改后的内容重新成为"最新一条"。</p>
     */
    @TableField("published_at")
    private LocalDateTime publishedAt;

    /**
     * 创建人用户编号，仅审计展示，不参与业务判断。
     */
    @TableField("created_by")
    private Long createdBy;

    /**
     * 最后修改人用户编号，仅审计展示，不参与业务判断。
     */
    @TableField("updated_by")
    private Long updatedBy;

    /**
     * 创建时间，由数据库维护。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 修改时间，由数据库在更新时自动维护。
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

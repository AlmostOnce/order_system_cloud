package com.hbue.ordering.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 新建或更新首页公告的请求对象。
 *
 * <p>不包含状态字段：新建固定为草稿，状态变化只能通过发布/下线接口完成，
 * 避免管理端一次提交同时改内容和状态导致审计语义不清。开始时间必填，
 * 允许设为将来时间以实现定时生效；结束时间可空，表示长期有效。</p>
 *
 * @author order-system
 * @date 2026-11-26
 */
@Data
public class HomeAnnouncementSaveRequest {

    /**
     * 公告标题，必填，最长 60 个字符。
     */
    @NotBlank
    @Size(max = 60)
    private String title;

    /**
     * 公告正文，必填，最长 500 个字符；仅纯文本，不做富文本解析。
     */
    @NotBlank
    @Size(max = 500)
    private String content;

    /**
     * 生效开始时间，必填，按项目本地时间 Asia/Shanghai 解释。
     */
    @NotNull
    private LocalDateTime startAt;

    /**
     * 生效结束时间；为空表示长期有效，非空时必须晚于 startAt。
     */
    private LocalDateTime endAt;
}

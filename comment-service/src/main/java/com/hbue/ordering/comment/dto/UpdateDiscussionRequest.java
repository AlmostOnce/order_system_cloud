package com.hbue.ordering.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 编辑评论的请求体。
 *
 * <p>{@code version} 是客户端读取评论时拿到的版本号，用于乐观并发：两次编辑同时到达时
 * 只有版本仍一致的那次生效，另一次返回冲突让客户端刷新，避免"后写静默覆盖前写"。</p>
 *
 * @param content 新正文，纯文本，1 至 500 字
 * @param version 客户端看到的评论版本号
 * @author order-system
 * @date 2026-11-25
 */
public record UpdateDiscussionRequest(
        @NotBlank(message = "评论内容不能为空")
        @Size(max = 500, message = "评论内容不能超过500字")
        String content,

        @Positive(message = "评论版本号必须为正数")
        int version
) {
}

package com.hbue.ordering.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发表评论或回复的请求体。
 *
 * <p>只接收正文。评价类型、评价标识、楼层根、父评论与作者身份全部由路径参数和
 * 已认证令牌决定，客户端不能通过请求体指定，避免伪造归属或把回复挂到他人楼层。</p>
 *
 * @param content 评论正文，纯文本，1 至 500 字
 * @author order-system
 * @date 2026-11-25
 */
public record CreateDiscussionRequest(
        @NotBlank(message = "评论内容不能为空")
        @Size(max = 500, message = "评论内容不能超过500字")
        String content
) {
}

package com.hbue.ordering.comment.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 店铺评价的公开视图。
 *
 * <p>末尾两个字段是自由评论区接入后新增的：{@code discussionCount} 是顶层评论数，
 * {@code discussions} 是最新若干条顶层评论预览。旧客户端忽略新字段即可。</p>
 *
 * @param reviewId        公开评价标识
 * @param windowId        档口编号
 * @param authorName      作者展示名
 * @param authorAvatarUrl 作者头像地址
 * @param score           评分
 * @param content         正文
 * @param images          图片地址
 * @param tags            标签
 * @param createdAt       发布时间
 * @param discussionCount 顶层评论数
 * @param discussions     顶层评论预览，最多三条
 * @author order-system
 * @date 2026-10-28
 */
public record WindowReviewVO(String reviewId, Long windowId, String authorName, String authorAvatarUrl, int score,
                             String content, List<String> images, List<String> tags, LocalDateTime createdAt,
                             int discussionCount, List<ReviewDiscussionVO> discussions) {
}

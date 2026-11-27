package com.hbue.ordering.comment.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价评论的公开视图。
 *
 * <p>不返回评论人内部编号、状态码与请求键。{@code official} 由评论时的角色快照派生，
 * 决定前端是否展示"商家"标识；{@code mine} 表示当前登录用户是否为作者，
 * 决定是否显示编辑与撤回入口。</p>
 *
 * <p>楼层结构：顶层评论的 {@code replies} 是最多若干条预览，{@code replyCount} 是完整回复数；
 * 楼层回复接口返回的 {@code replies} 为空，避免递归嵌套。{@code parentAuthorName}
 * 用于展示"回复 @某某"，父评论已撤回或屏蔽时由服务端给出占位文案。</p>
 *
 * @param discussionId     公开评论标识
 * @param reviewType       所属评价类型：COMMENT 或 WINDOW_REVIEW
 * @param reviewId         所属评价公开标识
 * @param rootId           楼层根评论标识
 * @param parentId         被回复评论标识，顶层评论为 null
 * @param depth            楼层深度，1 顶层、2 回复
 * @param authorName       作者展示名快照
 * @param authorAvatarUrl  作者头像地址，解析失败时为空串
 * @param authorRole       作者角色编码
 * @param official         是否为商家官方发言
 * @param parentAuthorName 被回复人的展示名，顶层评论为 null
 * @param content          评论正文
 * @param version          内容版本，编辑时需原样回传
 * @param createdAt        首次发布时间
 * @param updatedAt        最近编辑或状态变更时间，未变更为 null
 * @param replyCount       该楼层下的可见回复数；非顶层评论为 0
 * @param replies          楼层预览回复；仅顶层评论且列表接口填充
 * @param mine             当前登录用户是否为作者
 * @author order-system
 * @date 2026-11-25
 */
public record ReviewDiscussionVO(
        String discussionId,
        String reviewType,
        String reviewId,
        String rootId,
        String parentId,
        int depth,
        String authorName,
        String authorAvatarUrl,
        String authorRole,
        boolean official,
        String parentAuthorName,
        String content,
        int version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        int replyCount,
        List<ReviewDiscussionVO> replies,
        boolean mine
) {
}

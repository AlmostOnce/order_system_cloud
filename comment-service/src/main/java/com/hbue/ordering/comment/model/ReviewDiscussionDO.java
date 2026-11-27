package com.hbue.ordering.comment.model;

import java.time.LocalDateTime;

/**
 * 评价评论的内部持久化模型，不直接序列化到公开接口。
 *
 * <p>字段与 {@code review_discussion} 表一一对应。公开接口只暴露
 * {@code discussionId} 等公开标识，{@code authorId}、{@code status}、{@code version}、
 * {@code requestKey} 属于内部信息，不能出现在响应里。</p>
 *
 * <p>楼层关系：顶层评论的 {@code rootId} 等于自身 {@code discussionId}、{@code parentId} 为空；
 * 楼中楼回复的 {@code rootId} 指向楼层根、{@code parentId} 指向被回复的那条，
 * {@code depth} 恒为 2。</p>
 *
 * @param discussionId 公开评论标识
 * @param reviewType   所属评价类型编码
 * @param reviewId     所属评价公开标识
 * @param windowId     评价所属档口编号
 * @param rootId       楼层根评论标识
 * @param parentId     被直接回复的评论标识，顶层评论为 null
 * @param depth        楼层深度，1 顶层、2 回复
 * @param authorId     评论人内部用户编号
 * @param authorRole   评论时的角色快照
 * @param authorName   评论时的展示名快照
 * @param content      评论正文
 * @param status       状态：1 已发布，2 作者撤回，3 管理员屏蔽
 * @param version      内容版本
 * @param requestKey   客户端幂等请求键
 * @param createdAt    首次发布时间
 * @param updatedAt    最近编辑、撤回或屏蔽时间，未变更为 null
 * @author order-system
 * @date 2026-11-25
 */
public record ReviewDiscussionDO(
        String discussionId,
        String reviewType,
        String reviewId,
        Long windowId,
        String rootId,
        String parentId,
        int depth,
        Long authorId,
        String authorRole,
        String authorName,
        String content,
        int status,
        int version,
        String requestKey,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

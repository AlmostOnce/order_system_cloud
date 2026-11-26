package com.hbue.ordering.comment.vo;

import java.util.List;

/**
 * 评论分页结果。
 *
 * <p>{@code nextCursor} 是上一页最后一条的公开评论标识，客户端原样带回即可；
 * 没有下一页时为 null。{@code hasMore} 与游标同时返回，避免客户端靠条数猜测。</p>
 *
 * @param items      当前页评论
 * @param nextCursor 下一页游标，无下一页时为 null
 * @param hasMore    是否还有下一页
 * @author order-system
 * @date 2026-11-25
 */
public record ReviewDiscussionPageVO(
        List<ReviewDiscussionVO> items,
        String nextCursor,
        boolean hasMore
) {
}

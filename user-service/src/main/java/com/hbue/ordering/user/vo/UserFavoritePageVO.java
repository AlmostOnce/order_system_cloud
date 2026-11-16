package com.hbue.ordering.user.vo;

import java.util.List;

/**
 * 收藏关系的游标分页结果。
 *
 * <p>下一页只在确实还有数据时提供末条收藏 ID，客户端不能把它当作任意页码跳转。</p>
 *
 * @param items 当前页收藏关系
 * @param nextCursor 下一页游标；没有下一页时为 null
 * @param hasMore 是否仍有下一页
 */
public record UserFavoritePageVO(List<UserFavoriteItemVO> items,
                                 Long nextCursor,
                                 boolean hasMore) {
}

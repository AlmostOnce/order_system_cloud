package com.hbue.ordering.user.vo;

import java.util.List;

/**
 * 管理端账号检索的游标分页结果。
 *
 * <p>按内部主键倒序翻页，保证翻页期间新增账号不会导致已读数据重复或漏项。
 * {@code nextCursor} 只在 {@code hasMore} 为 true 时返回，客户端原样回传即可；
 * 切换筛选条件后必须丢弃旧游标并从第一页重新读取，否则游标可能落在新条件之外。</p>
 *
 * @param items      当前页账号摘要
 * @param nextCursor 下一页游标，没有更多数据时为 null
 * @param hasMore    服务端多取一条判断出的“是否还有下一页”，不由客户端猜测
 * @author order-system
 * @date 2026-11-28
 */
public record AdminUserPageVO(
        List<AdminUserSummaryVO> items,
        String nextCursor,
        boolean hasMore
) {
}

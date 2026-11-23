package com.hbue.ordering.support.vo;

import java.util.List;

/**
 * 工单游标分页结果。
 *
 * <p>不返回总数，避免大表 COUNT；额外读取一条判断是否还有下一页。
 * {@code nextCursor} 为空表示已经到底。</p>
 *
 * @param items      当前页工单
 * @param nextCursor 下一页游标，取当前页最后一条的公开标识
 * @param hasMore    是否还有下一页
 * @author order-system
 * @date 2026-11-23
 */
public record SupportTicketPageVO(
        List<SupportTicketSummaryVO> items,
        String nextCursor,
        boolean hasMore
) {
}

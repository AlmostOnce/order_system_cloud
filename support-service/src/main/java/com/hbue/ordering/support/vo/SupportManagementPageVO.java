package com.hbue.ordering.support.vo;

import java.util.List;

/**
 * 管理端工单游标分页结果。
 *
 * <p>管理端列表项包含提交人、餐厅范围与完整描述，因此不能复用学生端
 * {@link SupportTicketPageVO}。队列同样不返回总数，只用游标翻页。</p>
 *
 * @param items      当前页工单
 * @param nextCursor 下一页游标
 * @param hasMore    是否还有下一页
 * @author order-system
 * @date 2026-11-23
 */
public record SupportManagementPageVO(
        List<SupportManagementTicketVO> items,
        String nextCursor,
        boolean hasMore
) {
}

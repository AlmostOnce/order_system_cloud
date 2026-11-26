package com.hbue.ordering.comment.api;

/**
 * 评论数据库提交后的全量评分快照；版本由同一菜品的行锁串行递增。
 *
 * <p>快照包含"零评价"状态：某菜品的评价被全部删除后，评论服务会推送
 * {@code reviewCount=0}、{@code scoreTotal=0} 的全量快照，让商品库的评分投影回到无评分，
 * 因此校验条件按"零条"和"有条"分别约束，而不是一律要求条数大于 0。</p>
 *
 * @param version     事件版本，当前只支持 1（全量快照）
 * @param eventId     事件唯一标识，用于下游去重
 * @param productId   菜品编号
 * @param revision    该菜品的评分快照版本，单调递增
 * @param reviewCount 当前可见评价条数，允许为 0
 * @param scoreTotal  当前可见评价评分总和，条数为 0 时必须为 0
 * @author order-system
 * @date 2026-11-25
 */
public record ProductRatingChangedEvent(int version, String eventId, Long productId,
                                        long revision, long reviewCount, long scoreTotal) {

    /**
     * 校验事件字段与数值区间。
     *
     * <p>零评价时只要求总分为 0；非零时总分必须落在"每条至少 1 分、最多 5 分"的区间内，
     * 避免下游把明显错误的快照当成有效评分覆盖读模型。</p>
     *
     * @return 字段合法时返回 true
     */
    public boolean valid() {
        if (version != 1 || eventId == null
                || !eventId.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
                || productId == null || productId <= 0 || revision <= 0
                || reviewCount < 0 || scoreTotal < 0 || reviewCount > Long.MAX_VALUE / 5
                || scoreTotal > reviewCount * 5) {
            return false;
        }
        // 零条评价时总分必须为 0；有条评价时每条至少 1 分，因此总分不小于条数。
        return reviewCount == 0 ? scoreTotal == 0 : scoreTotal >= reviewCount;
    }
}

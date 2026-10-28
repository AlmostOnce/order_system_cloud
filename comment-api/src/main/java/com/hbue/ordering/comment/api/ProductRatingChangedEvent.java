package com.hbue.ordering.comment.api;

/** 评论数据库提交后的全量评分快照；版本由同一菜品的行锁串行递增。 */
public record ProductRatingChangedEvent(int version, String eventId, Long productId,
        long revision, long reviewCount, long scoreTotal) {
    public boolean valid() {
        return version == 1 && eventId != null && eventId.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
                && productId != null && productId > 0 && revision > 0 && reviewCount > 0
                && scoreTotal >= reviewCount && reviewCount <= Long.MAX_VALUE / 5
                && scoreTotal <= reviewCount * 5;
    }
}

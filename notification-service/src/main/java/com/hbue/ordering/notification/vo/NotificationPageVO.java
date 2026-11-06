package com.hbue.ordering.notification.vo;

import java.util.List;

/**
 * 系统通知游标分页结果。hasMore与nextCursor共同决定是否继续加载更早记录。
 * @param items 当前认证用户的一页系统通知
 * @param nextCursor 有下一页时为当前页最后一条通知UUID
 * @param hasMore 是否还存在更早记录
 */
public record NotificationPageVO(List<NotificationVO> items, String nextCursor, boolean hasMore) {}

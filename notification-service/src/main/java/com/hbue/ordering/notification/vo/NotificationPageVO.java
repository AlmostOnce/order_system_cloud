package com.hbue.ordering.notification.vo;

import java.util.List;

public record NotificationPageVO(List<NotificationVO> items, String nextCursor, boolean hasMore) {}

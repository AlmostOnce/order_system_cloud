package com.hbue.ordering.comment.vo;

import java.util.List;

public record WindowReviewPageVO(List<WindowReviewVO> items, String nextCursor, boolean hasMore) {}

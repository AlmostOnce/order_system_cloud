package com.hbue.ordering.comment.vo;
import java.util.List;
public record CommentPageVO(List<CommentVO> items, String nextCursor, boolean hasMore) {}

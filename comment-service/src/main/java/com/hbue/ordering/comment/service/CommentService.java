package com.hbue.ordering.comment.service;

import com.hbue.ordering.comment.dto.CreateCommentRequest;
import com.hbue.ordering.comment.vo.CommentPageVO;
import com.hbue.ordering.comment.vo.CommentVO;

/** 评论业务边界，供评论控制器调用。 */
public interface CommentService {
    CommentVO create(Long userId, String authorName, String bearer, String key, CreateCommentRequest input);
    CommentVO detail(String id);
    CommentPageVO page(Long userId, Long productId, String before, int limit);
}

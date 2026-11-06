package com.hbue.ordering.comment.service;

import com.hbue.ordering.comment.dto.CreateCommentRequest;
import com.hbue.ordering.comment.vo.CommentPageVO;
import com.hbue.ordering.comment.vo.CommentVO;

/** 评论业务边界，供评论控制器调用。 */
public interface CommentService {
    CommentVO create(Long userId, String authorName, String bearer, String key, CreateCommentRequest input);
    java.util.List<CommentVO> forOrder(Long userId, Long orderId);
    CommentVO detail(String id);
    /** score为空查询全部，只允许1或5；hasImages=true仅查有图，采用有界游标分页，用户身份由认证主体提供。 */
    CommentPageVO page(Long userId, Long productId, String before, int limit, Integer score, Boolean hasImages);
    /** 返回目标全部可见评价统计，不受当前页或当前筛选影响。 */
    com.hbue.ordering.comment.vo.ReviewSummaryVO summary(Long productId);
}

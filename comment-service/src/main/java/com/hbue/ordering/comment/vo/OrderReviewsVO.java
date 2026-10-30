package com.hbue.ordering.comment.vo;

import java.util.List;

/** 本人订单的店铺评价与逐菜评价，用于回显及防止重复填写。 */
public record OrderReviewsVO(Long orderId, WindowReviewVO windowReview, List<CommentVO> productReviews) {}

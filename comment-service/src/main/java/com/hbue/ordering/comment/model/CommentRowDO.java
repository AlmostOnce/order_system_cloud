package com.hbue.ordering.comment.model;

import java.time.LocalDateTime;

/**
 * 内部元数据模型，不直接序列化到公开接口。
 *
 * <p>{@code authorName} 始终保存发布时的真实昵称快照，{@code anonymous} 只表示对外展示是否脱敏：
 * 匿名评价在读取时把昵称截断为“首字 + 星号”并隐藏头像，真实昵称不出库，但内部仍保留可供
 * 追溯与申诉核查的事实。</p>
 */
public record CommentRowDO(String commentId, Long userId, String authorName, Long orderId,
                           Long productId, Long windowId, int score, String requestKey, String requestHash,
                           LocalDateTime createdAt,
                           // 元数据事务同步写入是否带图；null表示历史/旧代码写入记录尚未完成回填，不能当作无图。
                           Boolean hasImages,
                           // 是否匿名展示；0 实名、1 匿名，历史数据默认为实名。
                           boolean anonymous) {
}

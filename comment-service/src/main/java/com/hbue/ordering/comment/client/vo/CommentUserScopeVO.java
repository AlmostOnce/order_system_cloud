package com.hbue.ordering.comment.client.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 用户服务返回的数据范围最小投影。
 *
 * <p>评论服务只关心"是否全平台、授权了哪些档口、授权了哪些餐厅"三项事实，
 * 因此这里只声明对应字段；用户服务后续新增字段时，{@link JsonIgnoreProperties}
 * 保证反序列化不会因为未知字段失败。</p>
 *
 * @param platform      是否全平台范围
 * @param windowIds     直接授权的档口编号集合，可能为 null
 * @param restaurantIds 授权的餐厅编号集合，可能为 null
 * @author order-system
 * @date 2026-11-25
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommentUserScopeVO(
        boolean platform,
        List<Long> windowIds,
        List<Long> restaurantIds
) {
}

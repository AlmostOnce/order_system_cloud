package com.hbue.ordering.user.vo;

import com.hbue.ordering.user.model.FavoriteTargetType;

import java.time.LocalDateTime;

/**
 * 收藏列表中的单条关系视图。
 *
 * <p>仅返回用户服务拥有的收藏元数据；窗口名、菜品名及图片由商品服务查询，
 * 防止用户服务保存过期或由客户端伪造的目录内容。</p>
 *
 * @param favoriteId 收藏关系游标
 * @param targetType 目标类型
 * @param targetId 商品服务中的窗口或菜品 ID
 * @param createdAt 收藏时间
 */
public record UserFavoriteItemVO(Long favoriteId,
                                 FavoriteTargetType targetType,
                                 Long targetId,
                                 LocalDateTime createdAt) {
}

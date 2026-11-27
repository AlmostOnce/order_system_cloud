package com.hbue.ordering.user.vo;

import java.util.List;

/**
 * 当前用户在所请求目标集合中已收藏的 ID。
 *
 * @param targetIds 已收藏目标 ID；顺序不保证，客户端按 ID 集合判断状态
 */
public record FavoriteStatusVO(List<Long> targetIds) {
}

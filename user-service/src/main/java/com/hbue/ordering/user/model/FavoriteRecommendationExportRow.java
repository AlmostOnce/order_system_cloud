package com.hbue.ordering.user.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 推荐服务回填读取的收藏关系及其在同一数据库快照中可见的逐关系版本。
 */
@Data
public class FavoriteRecommendationExportRow {
    /**
     * 收藏关系编号，只作有界递增分页游标，不用于判断推荐事件版本。
     */
    private Long favoriteId;
    /**
     * 用户库内部用户编号，仅供通过内部凭据验证的服务消费。
     */
    private Long userId;
    /**
     * 收藏类型，仅为 PRODUCT 菜品或 WINDOW 店铺。
     */
    private String targetType;
    /**
     * 商品服务的菜品或店铺编号。
     */
    private Long targetId;
    /**
     * 当前收藏创建时间，历史记录无规格或状态变更推断。
     */
    private LocalDateTime createdAt;
    /**
     * 该关系在本次一致读中最新可见的发件箱自增编号；历史无事件为 0。
     */
    private Long sourceVersion;
}

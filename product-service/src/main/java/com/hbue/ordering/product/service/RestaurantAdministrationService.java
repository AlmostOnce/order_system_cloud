package com.hbue.ordering.product.service;

import com.hbue.ordering.product.dto.request.RestaurantSaveRequest;
import com.hbue.ordering.product.vo.RestaurantAdminVO;

import java.util.List;

/**
 * 餐厅（食堂）管理服务。
 *
 * <p>角色门禁只放行餐厅经理与总经理，本服务再按 {@code operatorId} 做数据范围校验：
 * 新建、停用、调整展示排序需要全平台范围（总经理）；餐厅经理只能维护
 * 自己餐厅的名称、位置、电话、门面图与公告。餐厅是公开资源，越权统一返回 403；
 * 无效 ID、资源不存在及停用约束冲突分别按 400、404、409 业务错误返回。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
public interface RestaurantAdministrationService {

    /**
     * 查询操作者范围内的餐厅，包括已停用餐厅。
     *
     * <p>按展示排序升序返回，相同排序按餐厅 ID 升序兜底。</p>
     *
     * @param operatorId 当前登录操作者 ID
     * @return 餐厅管理列表
     */
    List<RestaurantAdminVO> listRestaurants(Long operatorId);

    /**
     * 新建餐厅；仅全平台范围（总经理）可操作。
     *
     * @param operatorId 当前登录操作者 ID
     * @param request 餐厅字段
     * @return 新建餐厅
     */
    RestaurantAdminVO createRestaurant(
            Long operatorId,
            RestaurantSaveRequest request
    );

    /**
     * 全量更新餐厅。
     *
     * @param operatorId 当前登录操作者 ID
     * @param restaurantId 餐厅 ID
     * @param request 餐厅字段
     * @return 更新后的餐厅
     */
    RestaurantAdminVO updateRestaurant(
            Long operatorId,
            Long restaurantId,
            RestaurantSaveRequest request
    );

    /**
     * 停用餐厅；餐厅下仍有启用档口时返回 409。
     *
     * @param operatorId 当前登录操作者 ID
     * @param restaurantId 餐厅 ID
     */
    void disableRestaurant(Long operatorId, Long restaurantId);
}

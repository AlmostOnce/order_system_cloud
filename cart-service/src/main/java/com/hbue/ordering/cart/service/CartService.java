package com.hbue.ordering.cart.service;

import com.hbue.ordering.cart.dto.request.CartItemAddRequest;
import com.hbue.ordering.cart.vo.CartVO;

/**
 * 顾客购物车业务契约，身份参数必须来自已验证的 JWT。
 *
 * <p>实现负责商品核验、数量规则及展示聚合；存储原子性由 Redis 持久化访问层保证。
 * 没有关系数据库表，因此不继承 MyBatis-Plus 的 IService。</p>
 *
 * @author order-system
 * @date 2026-10-17
 */
public interface CartService {
    /**
     * 查询当前用户购物车，批量取得当前价格及可售状态。
     *
     * @param userId 当前用户 ID
     * @return 分组后的购物车，空购物车返回空分组和零金额
     * @throws com.hbue.ordering.common.core.exception.BusinessException 身份无效或依赖服务不可用时抛出
     */
    CartVO getCart(Long userId);

    /**
     * 添加菜品，同窗口同菜品已存在时原子累加数量。
     *
     * @param userId 当前用户 ID
     * @param windowId 窗口 ID
     * @param request 菜品 ID 与本次增加的数量
     * @throws com.hbue.ordering.common.core.exception.BusinessException 参数错误、不可售或容量超限时抛出
     */
    void addItem(Long userId, Long windowId, CartItemAddRequest request);

    /**
     * 设置已有菜品的目标数量，不依赖商品服务，失效项也允许用户调整或随后删除。
     *
     * @param userId 当前用户 ID
     * @param windowId 窗口 ID
     * @param productId 菜品 ID
     * @param quantity 目标数量，必须大于零且不超配置上限
     * @throws com.hbue.ordering.common.core.exception.BusinessException 条目不存在或数量非法时抛出
     */
    void updateQuantity(Long userId, Long windowId, Long productId, Integer quantity);

    /**
     * 更新已有条目的勾选状态，false 为取消勾选。
     *
     * @param userId 当前用户 ID
     * @param windowId 窗口 ID
     * @param productId 菜品 ID
     * @param checked 目标勾选状态
     * @throws com.hbue.ordering.common.core.exception.BusinessException 参数缺失或条目不存在时抛出
     */
    void updateChecked(Long userId, Long windowId, Long productId, Boolean checked);

    /**
     * 幂等删除当前用户指定菜品。
     *
     * @param userId 当前用户 ID
     * @param windowId 窗口 ID
     * @param productId 菜品 ID
     */
    void removeItem(Long userId, Long windowId, Long productId);

    /**
     * 幂等清空指定窗口，保留其它窗口。
     *
     * @param userId 当前用户 ID
     * @param windowId 窗口 ID
     */
    void clearWindow(Long userId, Long windowId);
}

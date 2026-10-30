package com.hbue.ordering.product.api.client;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.api.client.fallback.WindowInternalClientFallbackFactory;
import com.hbue.ordering.product.api.vo.RestaurantInfoVO;
import com.hbue.ordering.product.api.vo.WindowInfoVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 档口与餐厅内部查询客户端。
 *
 * <p>供业务服务完成数据范围校验时使用：
 * 把档口换算为所属餐厅、确认餐厅真实存在。
 * 属于服务间内部调用，使用共享密钥 {@code X-Internal-Token} 鉴权，
 * 该路径不在 Gateway 路由白名单内，外部无法直接访问。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@FeignClient(
        name = "product-service",
        contextId = "windowInternalClient",
        fallbackFactory = WindowInternalClientFallbackFactory.class
)
public interface WindowInternalClient {
    /** 批量读取当前店铺名称，包含停用店铺，供历史订单展示。 */
    @GetMapping("/internal/windows/names")
    ApiResponse<java.util.Map<Long, String>> getWindowNames(
            @org.springframework.web.bind.annotation.RequestParam("windowIds") java.util.List<Long> windowIds,
            @RequestHeader("X-Internal-Token") String internalToken);


    /**
     * 查询档口基本信息。
     *
     * @param windowId 档口 ID
     * @param internalToken 服务间共享密钥
     * @return 档口信息；档口不存在时 data 为 null
     */
    @GetMapping("/internal/windows/{windowId}")
    ApiResponse<WindowInfoVO> getWindow(
            @PathVariable("windowId") Long windowId,
            @RequestHeader("X-Internal-Token") String internalToken
    );

    /**
     * 查询餐厅基本信息。
     *
     * @param restaurantId 餐厅 ID
     * @param internalToken 服务间共享密钥
     * @return 餐厅信息；餐厅不存在时 data 为 null
     */
    @GetMapping("/internal/restaurants/{restaurantId}")
    ApiResponse<RestaurantInfoVO> getRestaurant(
            @PathVariable("restaurantId") Long restaurantId,
            @RequestHeader("X-Internal-Token") String internalToken
    );
}

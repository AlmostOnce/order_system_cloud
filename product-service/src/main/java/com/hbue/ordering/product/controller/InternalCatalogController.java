package com.hbue.ordering.product.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.product.api.vo.RestaurantInfoVO;
import com.hbue.ordering.product.api.vo.WindowInfoVO;
import com.hbue.ordering.product.mapper.DiningWindowMapper;
import com.hbue.ordering.product.mapper.RestaurantMapper;
import com.hbue.ordering.product.model.DiningWindowDO;
import com.hbue.ordering.product.model.RestaurantDO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 档口与餐厅内部查询接口。
 *
 * <p>供 order-service、user-service 完成数据范围校验时使用：
 * 把档口换算为所属餐厅、确认餐厅真实存在。
 * 本接口不在 Gateway 路由内，外部无法访问，调用方需携带共享密钥。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@RestController
@RequestMapping("/internal")
public class InternalCatalogController {

    /** 窗口数据 Mapper。 */
    private final DiningWindowMapper diningWindowMapper;

    /** 餐厅数据 Mapper。 */
    private final RestaurantMapper restaurantMapper;

    /** 内部调用共享密钥校验器。 */
    private final InternalScopeTokenVerifier internalScopeTokenVerifier;

    /**
     * 创建档口与餐厅内部查询接口。
     *
     * @param diningWindowMapper 窗口数据 Mapper
     * @param restaurantMapper 餐厅数据 Mapper
     * @param internalScopeTokenVerifier 内部调用共享密钥校验器
     */
    public InternalCatalogController(
            DiningWindowMapper diningWindowMapper,
            RestaurantMapper restaurantMapper,
            InternalScopeTokenVerifier internalScopeTokenVerifier
    ) {
        this.diningWindowMapper = diningWindowMapper;
        this.restaurantMapper = restaurantMapper;
        this.internalScopeTokenVerifier = internalScopeTokenVerifier;
    }

    /**
     * 查询档口基本信息。
     *
     * @param windowId 档口 ID
     * @param internalToken 服务间共享密钥
     * @return 档口信息；档口不存在时 data 为 null
     */
    @GetMapping("/windows/{windowId}")
    public ApiResponse<WindowInfoVO> getWindow(
            @PathVariable("windowId") Long windowId,
            @RequestHeader(value = "X-Internal-Token", required = false)
            String internalToken
    ) {
        // 内部调用必须携带正确的共享密钥，失败时统一返回 401。
        internalScopeTokenVerifier.verify(internalToken);

        DiningWindowDO window = diningWindowMapper.selectById(windowId);
        if (window == null) {
            return ApiResponse.success(null);
        }
        return ApiResponse.success(WindowInfoVO.builder()
                .windowId(window.getWindowId())
                .restaurantId(window.getRestaurantId())
                .status(window.getStatus())
                .build());
    }

    /**
     * 查询餐厅基本信息。
     *
     * @param restaurantId 餐厅 ID
     * @param internalToken 服务间共享密钥
     * @return 餐厅信息；餐厅不存在时 data 为 null
     */
    @GetMapping("/restaurants/{restaurantId}")
    public ApiResponse<RestaurantInfoVO> getRestaurant(
            @PathVariable("restaurantId") Long restaurantId,
            @RequestHeader(value = "X-Internal-Token", required = false)
            String internalToken
    ) {
        // 内部调用必须携带正确的共享密钥，失败时统一返回 401。
        internalScopeTokenVerifier.verify(internalToken);

        RestaurantDO restaurant = restaurantMapper.selectById(restaurantId);
        if (restaurant == null) {
            return ApiResponse.success(null);
        }
        return ApiResponse.success(RestaurantInfoVO.builder()
                .restaurantId(restaurant.getRestaurantId())
                .restaurantCode(restaurant.getRestaurantCode())
                .restaurantName(restaurant.getRestaurantName())
                .status(restaurant.getStatus())
                .build());
    }
}

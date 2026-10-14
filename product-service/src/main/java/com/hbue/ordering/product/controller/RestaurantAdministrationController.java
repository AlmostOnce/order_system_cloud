package com.hbue.ordering.product.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.dto.request.RestaurantSaveRequest;
import com.hbue.ordering.product.service.RestaurantAdministrationService;
import com.hbue.ordering.product.vo.RestaurantAdminVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 餐厅（食堂）管理接口。
 *
 * <p>角色门禁只放行餐厅经理与总经理。新建、停用与展示排序由 Service 按
 * 全平台范围二次校验；餐厅经理只能改动自己餐厅的展示信息。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Validated
@RestController
@Tag(name = "餐厅管理接口")
@RequestMapping("/products/admin/restaurants")
@PreAuthorize("hasAnyRole('RESTAURANT_MANAGER','GENERAL_MANAGER')")
public class RestaurantAdministrationController {

    /** 餐厅管理服务。 */
    private final RestaurantAdministrationService restaurantAdministrationService;

    /**
     * 创建餐厅管理 Controller。
     *
     * @param restaurantAdministrationService 餐厅管理服务
     */
    public RestaurantAdministrationController(
            RestaurantAdministrationService restaurantAdministrationService
    ) {
        this.restaurantAdministrationService = restaurantAdministrationService;
    }

    /**
     * 查询操作者范围内的餐厅，包括已停用餐厅。
     *
     * @param jwt 当前登录操作者 JWT
     * @return 餐厅列表
     */
    @GetMapping
    public ApiResponse<List<RestaurantAdminVO>> listRestaurants(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.success(
                restaurantAdministrationService.listRestaurants(operatorId(jwt))
        );
    }

    /**
     * 新建餐厅。
     *
     * @param jwt 当前登录操作者 JWT
     * @param request 餐厅字段
     * @return 新建餐厅
     */
    @PostMapping
    public ApiResponse<RestaurantAdminVO> createRestaurant(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RestaurantSaveRequest request
    ) {
        return ApiResponse.success(
                restaurantAdministrationService.createRestaurant(
                        operatorId(jwt),
                        request
                )
        );
    }

    /**
     * 全量更新餐厅。
     *
     * @param jwt 当前登录操作者 JWT
     * @param restaurantId 餐厅 ID
     * @param request 餐厅字段
     * @return 更新后的餐厅
     */
    @PutMapping("/{restaurantId}")
    public ApiResponse<RestaurantAdminVO> updateRestaurant(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("restaurantId") @Positive Long restaurantId,
            @Valid @RequestBody RestaurantSaveRequest request
    ) {
        return ApiResponse.success(
                restaurantAdministrationService.updateRestaurant(
                        operatorId(jwt),
                        restaurantId,
                        request
                )
        );
    }

    /**
     * 停用餐厅。
     *
     * @param jwt 当前登录操作者 JWT
     * @param restaurantId 餐厅 ID
     * @return 空成功响应
     */
    @DeleteMapping("/{restaurantId}")
    public ApiResponse<Void> disableRestaurant(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("restaurantId") @Positive Long restaurantId
    ) {
        restaurantAdministrationService.disableRestaurant(
                operatorId(jwt),
                restaurantId
        );
        return ApiResponse.success(null);
    }

    /**
     * 从 JWT 中取出当前操作者 ID。
     *
     * @param jwt 当前登录操作者 JWT
     * @return 操作者用户 ID
     */
    private Long operatorId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}

package com.hbue.ordering.cart.controller;

import com.hbue.ordering.cart.dto.request.CartItemAddRequest;
import com.hbue.ordering.cart.dto.request.CartItemCheckedRequest;
import com.hbue.ordering.cart.dto.request.CartItemQuantityRequest;
import com.hbue.ordering.cart.service.CartService;
import com.hbue.ordering.cart.vo.CartVO;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 顾客购物车 HTTP 入口，接收请求并调用 Service，不直接访问 Redis 或商品服务。
 *
 * <p>请求 DTO 只接收菜品、数量或勾选状态，所有操作的用户 ID 均来自已验证 JWT；
 * 前端无法通过请求体或查询参数操作其它用户的购物车。</p>
 *
 * @author order-system
 * @date 2026-10-17
 */
@RestController
@RequestMapping("/cart")
@Tag(name = "购物车接口")
public class CartController {

    /** 购物车业务服务契约。 */
    private final CartService cartService;

    /**
     * 创建购物车控制器。
     *
     * @param cartService 购物车业务服务
     */
    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    /**
     * 检查顾客请求是否已通过网关路由和本服务的身份、角色校验。
     *
     * @return 统一格式的服务运行提示
     */
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        // 此接口经过实际安全过滤链；返回成功表示本服务已接受当前顾客身份。
        return ApiResponse.success("cart-service is running");
    }

    /**
     * 查询当前顾客的购物车，展示当前价格与失效项。
     *
     * @param jwt 已通过资源服务器校验的用户令牌
     * @return 按窗口分组的购物车
     */
    @GetMapping
    public ApiResponse<CartVO> getCart(@AuthenticationPrincipal Jwt jwt) {
        // 身份只从 JWT 读取，Service 负责核价和金额聚合。
        return ApiResponse.success(cartService.getCart(userId(jwt)));
    }

    /**
     * 添加指定窗口的菜品，重复提交会继续累加数量。
     *
     * @param jwt 当前用户令牌
     * @param windowId 窗口 ID
     * @param request 菜品和增加数量
     * @return 写入成功响应，客户端可重新 GET 取得当前展示数据
     */
    @PostMapping("/windows/{windowId}/items")
    public ApiResponse<Void> addItem(@AuthenticationPrincipal Jwt jwt,
                                     @PathVariable("windowId") Long windowId,
                                     @Valid @RequestBody CartItemAddRequest request) {
        cartService.addItem(userId(jwt), windowId, request);
        return ApiResponse.success(null);
    }

    /**
     * 设置已有条目的目标数量，不以零表示删除。
     *
     * @param jwt 当前用户令牌
     * @param windowId 窗口 ID
     * @param productId 菜品 ID
     * @param request 目标数量
     * @return 更新成功响应
     */
    @PutMapping("/windows/{windowId}/items/{productId}")
    public ApiResponse<Void> updateQuantity(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("windowId") Long windowId, @PathVariable("productId") Long productId,
            @Valid @RequestBody CartItemQuantityRequest request) {
        cartService.updateQuantity(userId(jwt), windowId, productId, request.getQuantity());
        return ApiResponse.success(null);
    }

    /**
     * 修改已有条目的勾选状态。
     *
     * @param jwt 当前用户令牌
     * @param windowId 窗口 ID
     * @param productId 菜品 ID
     * @param request 勾选状态，false 为合法值
     * @return 更新成功响应
     */
    @PatchMapping("/windows/{windowId}/items/{productId}/checked")
    public ApiResponse<Void> updateChecked(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("windowId") Long windowId, @PathVariable("productId") Long productId,
            @Valid @RequestBody CartItemCheckedRequest request) {
        cartService.updateChecked(userId(jwt), windowId, productId, request.getChecked());
        return ApiResponse.success(null);
    }

    /**
     * 删除单个菜品，重复删除不会报错。
     *
     * @param jwt 当前用户令牌
     * @param windowId 窗口 ID
     * @param productId 菜品 ID
     * @return 删除成功响应
     */
    @DeleteMapping("/windows/{windowId}/items/{productId}")
    public ApiResponse<Void> removeItem(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("windowId") Long windowId, @PathVariable("productId") Long productId) {
        cartService.removeItem(userId(jwt), windowId, productId);
        return ApiResponse.success(null);
    }

    /**
     * 清空一个窗口，保留用户其它窗口的菜品。
     *
     * @param jwt 当前用户令牌
     * @param windowId 窗口 ID
     * @return 清空成功响应
     */
    @DeleteMapping("/windows/{windowId}")
    public ApiResponse<Void> clearWindow(@AuthenticationPrincipal Jwt jwt,
                                        @PathVariable("windowId") Long windowId) {
        cartService.clearWindow(userId(jwt), windowId);
        return ApiResponse.success(null);
    }

    /**
     * 将经过验签的 JWT sub 转为正整数用户 ID，避免畸形身份引发内部 500。
     *
     * @param jwt 已认证 JWT
     * @return 当前用户 ID
     * @throws BusinessException sub 缺失、非数字或非正数时返回 401
     */
    private Long userId(Jwt jwt) {
        try {
            // 验签证明令牌来源可信，业务仍需确认 sub 符合用户主键的格式约定。
            long id = Long.parseLong(jwt.getSubject());
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException exception) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "登录用户身份无效");
        }
        throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "登录用户身份无效");
    }
}

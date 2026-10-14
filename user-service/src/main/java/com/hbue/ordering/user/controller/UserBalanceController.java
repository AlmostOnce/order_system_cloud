package com.hbue.ordering.user.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.dto.request.PayPasswordSetRequest;
import com.hbue.ordering.user.service.PayPasswordService;
import com.hbue.ordering.user.service.UserBalanceService;
import com.hbue.ordering.user.vo.UserBalanceVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户余额与支付密码接口。
 *
 * <p>余额与支付密码只对本人开放，用户 ID 一律取自 JWT 的 subject，
 * 不接受客户端传入，避免越权读取他人余额。支付密码独立于登录密码，
 * 未设置前禁止使用余额。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
@Validated
@RestController
@Tag(name = "余额与支付密码")
@RequestMapping("/users")
@PreAuthorize("hasRole('CUSTOMER')")
public class UserBalanceController {

    /** 用户余额业务服务。 */
    private final UserBalanceService userBalanceService;

    /** 支付密码业务服务。 */
    private final PayPasswordService payPasswordService;

    /**
     * 创建用户余额与支付密码接口。
     *
     * @param userBalanceService 用户余额业务服务
     * @param payPasswordService 支付密码业务服务
     */
    public UserBalanceController(
            UserBalanceService userBalanceService,
            PayPasswordService payPasswordService
    ) {
        this.userBalanceService = userBalanceService;
        this.payPasswordService = payPasswordService;
    }

    /**
     * 查询本人余额与支付密码设置状态。
     *
     * @param jwt 当前登录用户 JWT
     * @return 余额视图对象
     */
    @GetMapping("/me/balance")
    public ApiResponse<UserBalanceVO> getMyBalance(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.success(
                userBalanceService.getMyBalance(currentUserId(jwt))
        );
    }

    /**
     * 首次设置或修改支付密码。
     *
     * @param jwt 当前登录用户 JWT
     * @param request 支付密码设置请求
     * @return 无数据的成功响应
     */
    @PutMapping("/pay-password")
    public ApiResponse<Void> setPayPassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PayPasswordSetRequest request
    ) {
        payPasswordService.setPayPassword(currentUserId(jwt), request);
        return ApiResponse.success(null);
    }

    /**
     * 从 JWT 中取出当前用户 ID。
     *
     * @param jwt 当前登录用户 JWT
     * @return 当前用户 ID
     */
    private Long currentUserId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}

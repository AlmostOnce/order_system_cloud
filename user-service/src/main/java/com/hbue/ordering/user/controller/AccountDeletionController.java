package com.hbue.ordering.user.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.dto.request.AccountDeletionAuthorizationRequest;
import com.hbue.ordering.user.dto.request.AccountDeletionCancelRequest;
import com.hbue.ordering.user.dto.request.AccountDeletionSubmitRequest;
import com.hbue.ordering.user.service.AccountDeletionService;
import com.hbue.ordering.user.vo.AccountDeletionAuthorizationVO;
import com.hbue.ordering.user.vo.AccountDeletionEligibilityVO;
import com.hbue.ordering.user.vo.AccountDeletionSubmitVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生账号注销接口。
 *
 * <p>资格查询、发送验证码、换取授权令牌和提交注销都只允许 CUSTOMER 本人访问，
 * 用户编号取自 JWT 的 sub，请求体不能指定目标账号。撤回接口不要求访问令牌：
 * 提交注销后账号已失去正常会话，只能凭登录时签发的一次性撤回令牌操作。</p>
 *
 * @author order-system
 * @date 2026-11-24
 */
@RestController
@RequestMapping("/users/me/deletion")
public class AccountDeletionController {

    /**
     * 账号注销业务服务。
     */
    private final AccountDeletionService accountDeletionService;

    /**
     * 创建账号注销接口。
     *
     * @param accountDeletionService 账号注销业务服务
     */
    public AccountDeletionController(AccountDeletionService accountDeletionService) {
        this.accountDeletionService = accountDeletionService;
    }

    /**
     * 查询当前账号的注销资格、余额与阻断项。
     *
     * @param jwt 当前登录凭据
     * @return 注销资格视图
     */
    @GetMapping("/eligibility")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<AccountDeletionEligibilityVO> eligibility(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(accountDeletionService.eligibility(Long.valueOf(jwt.getSubject())));
    }

    /**
     * 向当前绑定邮箱发送注销验证码。
     *
     * @param jwt 当前登录凭据
     * @return 成功响应，无业务数据
     */
    @PostMapping("/verification-code")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<Void> sendVerificationCode(@AuthenticationPrincipal Jwt jwt) {
        accountDeletionService.sendVerificationCode(Long.valueOf(jwt.getSubject()));
        return ApiResponse.success(null);
    }

    /**
     * 校验注销验证码并换取一次性授权令牌。
     *
     * @param jwt     当前登录凭据
     * @param request 6 位验证码
     * @return 一次性授权令牌
     */
    @PostMapping("/authorization")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<AccountDeletionAuthorizationVO> authorize(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AccountDeletionAuthorizationRequest request) {
        return ApiResponse.success(accountDeletionService.authorize(Long.valueOf(jwt.getSubject()), request));
    }

    /**
     * 提交注销申请。
     *
     * <p>成功后账号立即退出登录并进入 72 小时冷静期；客户端应清除本机登录态。</p>
     *
     * @param jwt     当前登录凭据
     * @param request 授权令牌与余额放弃确认
     * @return 注销生效时间
     */
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<AccountDeletionSubmitVO> submit(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AccountDeletionSubmitRequest request) {
        return ApiResponse.success(accountDeletionService.submit(
                Long.valueOf(jwt.getSubject()),
                jwt.getId(),
                jwt.getExpiresAt(),
                request
        ));
    }

    /**
     * 凭一次性撤回令牌撤回注销申请。
     *
     * <p>此接口匿名可访问，因为账号在注销冷静期内无法获得正常访问令牌；
     * 安全性由 10 分钟有效、一次性消费的随机令牌保证。</p>
     *
     * @param request 撤回令牌
     * @return 成功响应，无业务数据
     */
    @PostMapping("/cancel")
    public ApiResponse<Void> cancel(@Valid @RequestBody AccountDeletionCancelRequest request) {
        accountDeletionService.cancel(request);
        return ApiResponse.success(null);
    }
}

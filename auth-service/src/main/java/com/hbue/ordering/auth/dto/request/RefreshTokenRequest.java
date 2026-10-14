package com.hbue.ordering.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Refresh Token 操作请求 DTO。
 *
 * <p>用于刷新和注销接口。该字段是敏感凭据，只应由认证服务校验或撤销，
 * 不得记录到日志，也不应作为普通业务数据返回。</p>
 *
 * @author order-system
 * @date 2026-09-23
 */
@Data
public class RefreshTokenRequest {

    /**
     * Refresh Token。
     */
    @NotBlank(message = "Refresh Token 不能为空")
    private String refreshToken;
}

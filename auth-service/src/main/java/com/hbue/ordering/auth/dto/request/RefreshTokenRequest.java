package com.hbue.ordering.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Refresh Token 刷新请求对象。
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

package com.hbue.ordering.user.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 启用或禁用用户账号的请求 DTO。
 *
 * <p>仅承载目标状态，合法值由字段约束限定为 {@code 1}（启用）或
 * {@code 0}（禁用）；操作者身份和目标用户 ID 分别来自 JWT 与 URL 路径，
 * 不由请求体提供。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Data
public class UserStatusUpdateRequest {

    /** 目标状态：1 启用，0 禁用。 */
    @NotNull
    @Min(0)
    @Max(1)
    private Integer status;
}

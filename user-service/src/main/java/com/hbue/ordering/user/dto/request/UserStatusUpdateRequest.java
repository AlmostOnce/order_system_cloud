package com.hbue.ordering.user.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 启用或禁用账号的请求对象。
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

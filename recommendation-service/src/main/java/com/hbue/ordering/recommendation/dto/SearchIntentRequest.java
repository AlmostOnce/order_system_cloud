package com.hbue.ordering.recommendation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 搜索服务端认证后转发的一次成功首屏意图，不含浏览器可任意指定的未验证用户主体。 */
public record SearchIntentRequest(
        @NotNull Long userId,
        @NotBlank @Size(max = 64) String queryId,
        @NotBlank @Size(max = 80) String keyword,
        Long restaurantId,
        Long windowId,
        Long categoryId,
        @NotNull @Size(max = 20) List<Long> rankedProductIds
) { }

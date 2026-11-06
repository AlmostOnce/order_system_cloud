package com.hbue.ordering.product.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 管理端提交完整有序图片列表：最多5张，空数组清空，第一张作为封面；不接受客户端URL。 */
public record WindowImagesRequest(
        @NotNull @Size(max = 5, message = "店铺最多设置5张图片")
        List<@NotBlank @Pattern(regexp = "[a-fA-F0-9]{32}") String> fileKeys) {}

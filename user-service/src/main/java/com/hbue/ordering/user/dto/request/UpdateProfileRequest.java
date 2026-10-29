package com.hbue.ordering.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 仅允许更新本人的昵称。邮箱使用独立的安全接口变更。 */
public record UpdateProfileRequest(
        @NotBlank(message = "昵称不能为空") @Size(min = 2, max = 64, message = "昵称长度为2至64个字符") String username) { }

package com.hbue.ordering.common.web.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 业务接口只接收文件服务生成的键，不接受任意图片 URL。 */
public record ImageFileRequest(
        @NotBlank @Pattern(regexp = "[a-fA-F0-9]{32}", message = "图片文件键格式不正确") String fileKey) {
}

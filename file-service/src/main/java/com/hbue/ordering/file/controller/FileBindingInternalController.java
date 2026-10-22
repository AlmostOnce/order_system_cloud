package com.hbue.ordering.file.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.file.service.FileStorageService;
import org.springframework.web.bind.annotation.*;

/** 文件归属只供服务端验证，不向浏览器返回上传者内部主键。 */
@RestController
@RequestMapping("/internal/files")
public class FileBindingInternalController {
    private final InternalScopeTokenVerifier verifier;
    private final FileStorageService files;
    public FileBindingInternalController(InternalScopeTokenVerifier verifier, FileStorageService files) { this.verifier = verifier; this.files = files; }

    public record BindingMeta(String fileKey, String bizType, Long bizRef, String contentType, Long uploaderId) {}

    @GetMapping("/{fileKey}")
    public ApiResponse<BindingMeta> metadata(@RequestHeader(value = "X-Internal-Token", required = false) String token,
            @PathVariable("fileKey") String fileKey) {
        verifier.verify(token);
        var file = files.getMeta(fileKey);
        return ApiResponse.success(new BindingMeta(file.getFileKey(), file.getBizType(), file.getBizRef(), file.getContentType(), file.getUploaderId()));
    }
}

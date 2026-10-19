package com.hbue.ordering.file.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.file.service.FileContent;
import com.hbue.ordering.file.service.FileStorageService;
import com.hbue.ordering.file.vo.FileMetaVO;
import com.hbue.ordering.file.vo.FileUploadVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件上传、元数据与内容读取接口。
 *
 * <p>上传、元数据查询与删除需要登录；内容读取接口匿名开放，便于前端直接以
 * 返回地址引用图片。删除的归属与角色判定在 Service 层完成，Controller 只负责
 * 取出 JWT 声明并转交业务调用。</p>
 *
 * @author order-system
 * @date 2026-10-16
 */
@RestController
@Tag(name = "文件接口")
@RequestMapping("/files")
public class FileController {

    /** 系统管理员角色编码。 */
    private static final String ROLE_SYSTEM_ADMIN = "SYSTEM_ADMIN";

    /** 内容响应缓存时长（秒）。 */
    private static final long CONTENT_CACHE_SECONDS = 3600L;

    /** 文件存储服务。 */
    private final FileStorageService fileStorageService;

    /**
     * 创建文件接口 Controller。
     *
     * @param fileStorageService 文件存储服务
     */
    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * 上传文件并登记元数据。
     *
     * @param jwt 当前登录用户 JWT
     * @param file 上传的文件
     * @param bizType 业务类型，可选
     * @param bizRef 关联业务主键，可选
     * @return 上传结果
     */
    @PostMapping("/upload")
    public ApiResponse<FileUploadVO> upload(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "bizRef", required = false) Long bizRef
    ) {
        // 上传者取自 JWT 的 sub 声明。
        Long uploaderId = Long.valueOf(jwt.getSubject());

        // 转交服务层完成校验、落盘与元数据登记。
        return ApiResponse.success(
                fileStorageService.upload(file, bizType, bizRef, uploaderId)
        );
    }

    /**
     * 按文件键查询文件元数据。
     *
     * @param fileKey 对外文件键
     * @return 文件元数据
     */
    @GetMapping("/{fileKey}")
    public ApiResponse<FileMetaVO> getMeta(
            @PathVariable("fileKey") String fileKey
    ) {
        return ApiResponse.success(fileStorageService.getMeta(fileKey));
    }

    /**
     * 按文件键读取文件内容，供图片直接引用。
     *
     * @param fileKey 对外文件键
     * @return 文件字节内容响应
     */
    @GetMapping("/{fileKey}/content")
    public ResponseEntity<byte[]> loadContent(
            @PathVariable("fileKey") String fileKey
    ) {
        // 从服务层读取内容、类型与原始文件名。
        FileContent fileContent = fileStorageService.loadContent(fileKey);

        // 设置内容类型与内联展示的文件名，禁止浏览器嗅探类型。
        HttpHeaders headers = new HttpHeaders();
        if (fileContent.getContentType() != null) {
            headers.setContentType(
                    MediaType.parseMediaType(fileContent.getContentType())
            );
        }
        headers.set(
                HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"" + safeFileName(fileContent.getOriginalName()) + "\""
        );
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set(
                HttpHeaders.CACHE_CONTROL,
                "public, max-age=" + CONTENT_CACHE_SECONDS
        );

        // 以字节数组返回文件内容。
        return ResponseEntity.ok()
                .headers(headers)
                .body(fileContent.getContent());
    }

    /**
     * 逻辑删除文件记录。
     *
     * @param jwt 当前登录用户 JWT
     * @param fileKey 对外文件键
     * @return 空成功响应
     */
    @DeleteMapping("/{fileKey}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("fileKey") String fileKey
    ) {
        // 操作者取自 JWT 的 sub 声明。
        Long operatorId = Long.valueOf(jwt.getSubject());

        // 是否系统管理员由 JWT 的 roles 声明判定。
        fileStorageService.delete(fileKey, operatorId, isSystemAdmin(jwt));

        return ApiResponse.success(null);
    }

    /**
     * 判断当前 JWT 是否持有系统管理员角色。
     *
     * @param jwt 当前登录用户 JWT
     * @return 持有 SYSTEM_ADMIN 角色返回 true
     */
    private boolean isSystemAdmin(Jwt jwt) {
        // 取 roles 声明，缺失时视为无角色。
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null) {
            return false;
        }

        // 使用普通循环判断是否包含系统管理员角色。
        for (String role : roles) {
            if (ROLE_SYSTEM_ADMIN.equals(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 清洗响应头中的文件名，去除引号与换行避免响应头注入。
     *
     * @param originalName 原始文件名
     * @return 可安全写入响应头的文件名
     */
    private String safeFileName(String originalName) {
        // 文件名缺失时使用占位名。
        if (originalName == null || originalName.isBlank()) {
            return "file";
        }

        // 去除引号与换行等会破坏响应头结构的字符。
        return originalName.replace("\"", "").replace("\r", "").replace("\n", "");
    }
}

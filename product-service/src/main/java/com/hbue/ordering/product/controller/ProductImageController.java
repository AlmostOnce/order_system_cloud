package com.hbue.ordering.product.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.media.ImageFileRequest;
import com.hbue.ordering.product.service.ProductAdministrationService;
import com.hbue.ordering.product.dto.request.WindowImagesRequest;
import com.hbue.ordering.product.vo.ProductAdminVO;
import com.hbue.ordering.product.vo.WindowAdminVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** 店铺及菜品图片管理接口；角色门禁沿用目录管理，具体窗口范围由Service再次校验。 */
@Validated
@RestController
@RequestMapping("/products/admin")
@PreAuthorize("hasAnyRole('STALL_MANAGER','RESTAURANT_MANAGER')")
public class ProductImageController {
    /** 复用目录管理服务，图片操作沿用相同的经营范围、事务与缓存失效规则。 */
    private final ProductAdministrationService catalog;

    /** 注入已有目录服务，不在Controller直接操作文件或数据库。 */
    public ProductImageController(ProductAdministrationService catalog) {
        this.catalog = catalog;
    }

    /** 完整替换店铺图片，不覆盖名称等字段；操作者来自JWT，禁止客户端指定店主身份。 */
    @PutMapping("/windows/{windowId}/images")
    public ApiResponse<WindowAdminVO> gallery(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("windowId") @Positive Long windowId, @Valid @RequestBody WindowImagesRequest request) {
        return ApiResponse.success(catalog.updateWindowImages(Long.valueOf(jwt.getSubject()), windowId, request.fileKeys()));
    }

    /** 兼容旧单图入口：更新封面时保留其余展示图片，最多五张规则同样适用。 */
    @PutMapping("/windows/{windowId}/image")
    public ApiResponse<WindowAdminVO> window(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("windowId") @Positive Long windowId, @Valid @RequestBody ImageFileRequest request) {
        Long operator = Long.valueOf(jwt.getSubject());
        return ApiResponse.success(catalog.updateWindowImage(operator, windowId, request.fileKey()));
    }

    /** 菜品仍使用单图；只扩展店铺图片，不改变菜品原有保存契约。 */
    @PutMapping("/products/{productId}/image")
    public ApiResponse<ProductAdminVO> product(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("productId") @Positive Long productId, @Valid @RequestBody ImageFileRequest request) {
        Long operator = Long.valueOf(jwt.getSubject());
        return ApiResponse.success(catalog.updateProductImage(operator, productId, request.fileKey()));
    }
}

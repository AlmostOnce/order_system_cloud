package com.hbue.ordering.product.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.media.ImageFileRequest;
import com.hbue.ordering.product.service.ProductAdministrationService;
import com.hbue.ordering.product.vo.ProductAdminVO;
import com.hbue.ordering.product.vo.WindowAdminVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/products/admin")
@PreAuthorize("hasAnyRole('STALL_MANAGER','RESTAURANT_MANAGER')")
public class ProductImageController {
    private final ProductAdministrationService catalog;

    public ProductImageController(ProductAdministrationService catalog) {
        this.catalog = catalog;
    }

    @PutMapping("/windows/{windowId}/image")
    public ApiResponse<WindowAdminVO> window(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("windowId") @Positive Long windowId, @Valid @RequestBody ImageFileRequest request) {
        Long operator = Long.valueOf(jwt.getSubject());
        return ApiResponse.success(catalog.updateWindowImage(operator, windowId, request.fileKey()));
    }

    @PutMapping("/products/{productId}/image")
    public ApiResponse<ProductAdminVO> product(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("productId") @Positive Long productId, @Valid @RequestBody ImageFileRequest request) {
        Long operator = Long.valueOf(jwt.getSubject());
        return ApiResponse.success(catalog.updateProductImage(operator, productId, request.fileKey()));
    }
}

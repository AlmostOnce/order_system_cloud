package com.hbue.ordering.product.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.dto.request.ProductSpecificationsSaveRequest;
import com.hbue.ordering.product.service.ProductSpecificationService;
import com.hbue.ordering.product.vo.ProductSpecificationGroupVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Vue管理端规格接口；只允许店主和餐厅经理，服务层再按JWT用户核对商品所属窗口的数据范围。 */
@Validated
@RestController
@Tag(name = "商品规格管理接口")
@RequestMapping("/products/admin/products/{productId}/specifications")
@PreAuthorize("hasAnyRole('STALL_MANAGER','RESTAURANT_MANAGER')")
public class ProductSpecificationController {
    /** 规格服务；Controller不直接访问Mapper，避免绕过范围和事务。 */
    private final ProductSpecificationService specifications;

    /** 注入负责权限和完整替换规则的服务。 */
    public ProductSpecificationController(ProductSpecificationService specifications) {
        this.specifications = specifications;
    }

    /** 读取启用配置；停用记录不暴露给管理表单，空配置返回空数组。 */
    @GetMapping
    public ApiResponse<List<ProductSpecificationGroupVO>> get(
            @AuthenticationPrincipal Jwt jwt, @PathVariable("productId") @Positive Long productId) {
        return ApiResponse.success(specifications.get(Long.valueOf(jwt.getSubject()), productId));
    }

    /** 完整替换启用配置；请求校验失败或范围越权时不会写入任何记录。 */
    @PutMapping
    public ApiResponse<List<ProductSpecificationGroupVO>> replace(
            @AuthenticationPrincipal Jwt jwt, @PathVariable("productId") @Positive Long productId,
            @Valid @RequestBody ProductSpecificationsSaveRequest request) {
        return ApiResponse.success(specifications.replace(Long.valueOf(jwt.getSubject()), productId, request));
    }
}

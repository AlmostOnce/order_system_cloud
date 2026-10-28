package com.hbue.ordering.product.rating;
import com.hbue.ordering.comment.api.ProductRatingVO;
import com.hbue.ordering.common.response.ApiResponse;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/products")
@Validated
public class ProductRatingController {
    private final ProductRatingProjection ratings;
    public ProductRatingController(ProductRatingProjection ratings) {this.ratings=ratings;}
    @GetMapping("/{productId}/rating")
    public ApiResponse<ProductRatingVO> get(@PathVariable("productId") @Positive Long productId) {
        return ApiResponse.success(ratings.get(productId));
    }
}

package com.hbue.ordering.product.api.dto;

import com.hbue.ordering.product.api.vo.ProductQuoteVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 商品服务的批量核价结果。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductQuoteResponse {

    /** 请求窗口是否启用。 */
    private boolean windowActive;

    /** 属于该窗口且当前在售的菜品；缺失的 ID 由调用方识别。 */
    private List<ProductQuoteVO> products;
}

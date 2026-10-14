package com.hbue.ordering.product.api.dto;

import com.hbue.ordering.product.api.vo.ProductQuoteVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 商品服务返回的批量核价响应 DTO。
 *
 * <p>{@code windowActive} 表示请求窗口当前是否启用；{@code products} 仅包含
 * 属于该窗口且当前允许下单的商品。请求 ID 未出现在结果中时，调用方不得
 * 使用客户端提交的名称或价格替代商品服务数据。</p>
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

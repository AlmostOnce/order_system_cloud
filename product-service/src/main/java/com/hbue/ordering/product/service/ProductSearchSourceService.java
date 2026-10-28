package com.hbue.ordering.product.service;

import com.hbue.ordering.product.api.dto.ProductSearchDocument;
import java.util.List;

/** 商品服务提供给搜索服务的可售菜品快照。 */
public interface ProductSearchSourceService {
    /** 返回可检索窗口的 ID。 */
    List<Long> searchableWindowIds();

    /** 返回一个窗口当前可售的菜品快照。 */
    List<ProductSearchDocument> documents(Long windowId);

    /** 返回单个可售菜品；无结果表示应从索引中移除。 */
    List<ProductSearchDocument> document(Long windowId, Long productId);
}

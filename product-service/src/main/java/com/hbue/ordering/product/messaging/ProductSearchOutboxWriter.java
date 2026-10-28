package com.hbue.ordering.product.messaging;

import org.springframework.stereotype.Component;
import com.hbue.ordering.product.mapper.ProductSearchOutboxMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** 在商品目录事务中登记搜索索引刷新任务。 */
@Component
public class ProductSearchOutboxWriter {
    private final ProductSearchOutboxMapper mapper;
    public ProductSearchOutboxWriter(ProductSearchOutboxMapper mapper) { this.mapper = mapper; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordWindow(Long windowId) {
        if (windowId != null) mapper.insert(UUID.randomUUID().toString(), windowId, null);
    }

    /** 登记单个菜品的变更，并与商品写入处于同一事务。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordProduct(Long windowId, Long productId) {
        if (windowId == null || productId == null) throw new IllegalArgumentException("窗口和菜品 ID 不能为空");
        mapper.insert(UUID.randomUUID().toString(), windowId, productId);
    }
}

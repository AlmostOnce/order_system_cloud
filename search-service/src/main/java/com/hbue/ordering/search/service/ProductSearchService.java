package com.hbue.ordering.search.service;

import com.hbue.ordering.search.vo.ProductSearchPageVO;

/** 菜品搜索业务边界，供 HTTP 控制器和消息消费者共同调用。 */
public interface ProductSearchService {
    ProductSearchPageVO search(String keyword, Long restaurantId, Long windowId, Long categoryId, int page, int size);
    void refreshWindow(Long windowId);
    void refreshProduct(Long windowId, Long productId);
    int rebuildAll();

    /**
     * 将商品服务中现有窗口的菜品同步到搜索索引。
     *
     * @return 已同步的窗口数量
     */
    int synchronizeExistingWindows();

    /** 对比商品快照和搜索索引，修复不一致的菜品，返回修复数量。 */
    int reconcile();
}

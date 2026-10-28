package com.hbue.ordering.search.service;

import com.hbue.ordering.search.vo.ProductSearchPageVO;

/** 菜品搜索业务边界，供 HTTP 控制器和消息消费者共同调用。 */
public interface ProductSearchService {
    ProductSearchPageVO search(String keyword, Long restaurantId, Long windowId, Long categoryId, int page, int size);
    void refreshWindow(Long windowId);
    int rebuildAll();
}

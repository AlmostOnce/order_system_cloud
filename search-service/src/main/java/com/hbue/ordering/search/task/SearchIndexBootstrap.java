package com.hbue.ordering.search.task;

import com.hbue.ordering.search.service.ProductSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 启动后同步已有菜品，依赖暂时不可用时保留重试机会。
 *
 * @author order-system
 * @date 2026-10-28
 */
@Component
public class SearchIndexBootstrap {
    private static final Logger log = LoggerFactory.getLogger(SearchIndexBootstrap.class);

    private final ProductSearchService searchService;
    private volatile boolean synchronizedOnce;

    public SearchIndexBootstrap(ProductSearchService searchService) {
        this.searchService = searchService;
    }

    /** 同步所有现有窗口；失败时在下一次调度继续重试。 */
    @Scheduled(initialDelayString = "${search.bootstrap.initial-delay-ms:5000}",
            fixedDelayString = "${search.bootstrap.retry-delay-ms:60000}")
    public void synchronize() {
        if (synchronizedOnce) {
            return;
        }
        try {
            int count = searchService.synchronizeExistingWindows();
            synchronizedOnce = true;
            log.info("搜索索引启动检查完成；空索引重建的窗口数={}", count);
        } catch (RuntimeException exception) {
            log.warn("搜索索引已有菜品同步失败，稍后重试", exception);
        }
    }
}

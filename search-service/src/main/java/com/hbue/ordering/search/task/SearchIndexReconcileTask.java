package com.hbue.ordering.search.task;

import com.hbue.ordering.search.service.ProductSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定期对比商品库与搜索索引，补偿遗漏或失败的消息。
 *
 * @author order-system
 * @date 2026-10-28
 */
@Component
public class SearchIndexReconcileTask {
    private static final Logger log = LoggerFactory.getLogger(SearchIndexReconcileTask.class);
    private final ProductSearchService searchService;

    public SearchIndexReconcileTask(ProductSearchService searchService) { this.searchService = searchService; }

    @Scheduled(cron = "${search.reconcile.cron:0 30 3 * * *}")
    public void reconcile() {
        try {
            int repaired = searchService.reconcile();
            log.info("搜索索引对账完成，修复菜品数={}", repaired);
        } catch (RuntimeException exception) {
            log.warn("搜索索引对账失败，等待下次执行", exception);
        }
    }
}

package com.hbue.ordering.search.config;

import com.hbue.ordering.search.support.SearchIndexManager;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 首次启动时按实体映射创建菜品索引。 */
@Configuration(proxyBeanMethods = false)
public class SearchIndexInitializer {
    @Bean
    public ApplicationRunner createMenuIndex(SearchIndexManager indexManager) {
        return args -> indexManager.initialize();
    }
}

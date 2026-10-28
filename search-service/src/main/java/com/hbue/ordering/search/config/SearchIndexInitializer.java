package com.hbue.ordering.search.config;

import com.hbue.ordering.search.model.MenuProductDocument;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

/** 首次启动时按实体映射创建菜品索引。 */
@Configuration(proxyBeanMethods = false)
public class SearchIndexInitializer {
    @Bean
    public ApplicationRunner createMenuIndex(ElasticsearchOperations operations) {
        return args -> {
            var index = operations.indexOps(MenuProductDocument.class);
            if (!index.exists()) index.createWithMapping();
        };
    }
}

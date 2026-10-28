package com.hbue.ordering.search.support;

import com.hbue.ordering.search.document.MenuProductDocument;
import java.util.Map;
import java.util.Set;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.index.AliasAction;
import org.springframework.data.elasticsearch.core.index.AliasActionParameters;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.index.AliasData;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

/**
 * 管理搜索别名和新旧物理索引；旧索引保留以便恢复。
 *
 * @author order-system
 * @date 2026-10-28
 */
@Component
public class SearchIndexManager {
    public static final String ALIAS = "menu-products-current";
    private static final String LEGACY_INDEX = "menu-products-v1";
    private final ElasticsearchOperations elasticsearch;
    private final SearchIndexLock indexLock;

    public SearchIndexManager(ElasticsearchOperations elasticsearch, SearchIndexLock indexLock) {
        this.elasticsearch = elasticsearch;
        this.indexLock = indexLock;
    }

    /** 首次升级时将已有索引接入别名，不删除原有文档。 */
    public void initialize() {
        indexLock.withWriteLock(() -> {
            if (elasticsearch.indexOps(IndexCoordinates.of(ALIAS)).exists()) return null;
            IndexOperations legacy = elasticsearch.indexOps(IndexCoordinates.of(LEGACY_INDEX));
            if (!legacy.exists()) createIndex(LEGACY_INDEX);
            AliasActions actions = new AliasActions(new AliasAction.Add(parameters(LEGACY_INDEX)));
            if (!legacy.alias(actions)) throw new IllegalStateException("创建搜索索引别名失败");
            return null;
        });
    }

    /** 根据菜品文档映射创建新的物理索引。 */
    public void createIndex(String name) {
        IndexOperations template = elasticsearch.indexOps(MenuProductDocument.class);
        IndexOperations target = elasticsearch.indexOps(IndexCoordinates.of(name));
        if (!target.create(template.createSettings(), template.createMapping())) {
            throw new IllegalStateException("创建搜索索引失败：" + name);
        }
    }

    /** 原子切换别名，调用方须持有索引写锁。 */
    public void switchAlias(String targetIndex) {
        IndexOperations operations = elasticsearch.indexOps(IndexCoordinates.of(ALIAS));
        Map<String, Set<AliasData>> previous = operations.getAliases(ALIAS);
        if (previous.isEmpty()) throw new IllegalStateException("搜索索引别名不存在");
        AliasActions actions = new AliasActions();
        for (String index : previous.keySet()) actions.add(new AliasAction.Remove(parameters(index)));
        actions.add(new AliasAction.Add(parameters(targetIndex)));
        if (!operations.alias(actions)) throw new IllegalStateException("搜索索引别名切换失败");
    }

    private AliasActionParameters parameters(String index) {
        return AliasActionParameters.builder().withIndices(index).withAliases(ALIAS).build();
    }
}

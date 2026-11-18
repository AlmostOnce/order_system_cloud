package com.hbue.ordering.product.service.impl;

import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.core.scope.DataScopeService;
import com.hbue.ordering.product.cache.ProductMenuCacheService;
import com.hbue.ordering.product.dto.request.ProductSpecificationsSaveRequest;
import com.hbue.ordering.product.mapper.ProductMapper;
import com.hbue.ordering.product.mapper.ProductSpecificationGroupMapper;
import com.hbue.ordering.product.mapper.ProductSpecificationOptionMapper;
import com.hbue.ordering.product.messaging.ProductSearchOutboxWriter;
import com.hbue.ordering.product.model.ProductDO;
import com.hbue.ordering.product.model.ProductSpecificationGroupDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 规格完整替换的边界用例；只维护源码，本次按约定不执行测试。
 */
@ExtendWith(MockitoExtension.class)
class ProductSpecificationServiceImplTest {
    /**
     * 商品锁读取与规格持久化均mock，不连接数据库。
     */
    @Mock
    private ProductMapper products;
    @Mock
    private ProductSpecificationGroupMapper groups;
    @Mock
    private ProductSpecificationOptionMapper options;
    /**
     * 权限、缓存和Outbox用mock确认调用边界。
     */
    @Mock
    private DataScopeService scopes;
    @Mock
    private ProductMenuCacheService menus;
    @Mock
    private ProductSearchOutboxWriter outbox;
    /**
     * 待测服务通过真实方法验证写入顺序和校验行为。
     */
    private ProductSpecificationServiceImpl service;

    /**
     * 每例从同一目标菜品开始，行锁读取成功后仍必须做窗口数据范围校验。
     */
    @BeforeEach
    void setUp() {
        ProductDO product = new ProductDO();
        product.setProductId(101L);
        product.setWindowId(3L);
        when(products.selectForSpecificationUpdate(101L)).thenReturn(product);
        service = new ProductSpecificationServiceImpl(products, groups, options, scopes, menus, outbox);
    }

    /**
     * 必选组没有零加价项时，必须在停用旧数据之前失败，避免不完整配置影响营业。
     */
    @Test
    void shouldRejectRequiredGroupWithoutZeroPriceBeforeWriting() {
        ProductSpecificationsSaveRequest.Option option = new ProductSpecificationsSaveRequest.Option();
        option.setName("大份");
        option.setAdditionalPrice(new BigDecimal("3.00"));
        ProductSpecificationsSaveRequest.Group group = new ProductSpecificationsSaveRequest.Group();
        group.setName("份量");
        group.setRequired(true);
        group.setOptions(List.of(option));
        ProductSpecificationsSaveRequest request = new ProductSpecificationsSaveRequest();
        request.setGroups(List.of(group));

        assertThrows(BusinessException.class, () -> service.replace(7L, 101L, request));
        verify(scopes).checkWindowAccess(7L, 3L);
        verify(groups, never()).update(any(), any());
        verifyNoInteractions(outbox, menus);
    }

    /**
     * 空组数组清除配置时只更新状态；缓存仅在提交回调后清除，Outbox在事务中登记。
     */
    @Test
    void shouldSoftDisableAndEvictOnlyAfterCommit() {
        ProductSpecificationGroupDO existing = new ProductSpecificationGroupDO();
        existing.setGroupId(11L);
        when(groups.selectList(any())).thenReturn(List.of(existing), List.of());
        ProductSpecificationsSaveRequest request = new ProductSpecificationsSaveRequest();
        request.setGroups(List.of());
        // 手动模拟事务同步上下文，只检查回调行为，不宣称验证数据库事务或回滚。
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertTrue(service.replace(7L, 101L, request).isEmpty());
            verify(groups).update(isNull(), any());
            verify(options).update(isNull(), any());
            verify(outbox).recordProduct(3L, 101L);
            verifyNoInteractions(menus);
            TransactionSynchronizationManager.getSynchronizations().forEach(callback -> callback.afterCommit());
            verify(menus).evictWindowMenu(3L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}

package com.hbue.ordering.product.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hbue.ordering.common.core.scope.DataScopeService;
import com.hbue.ordering.product.cache.ProductBloomFilterManager;
import com.hbue.ordering.product.cache.ProductMenuCacheService;
import com.hbue.ordering.product.dto.request.WindowSaveRequest;
import com.hbue.ordering.product.mapper.DiningWindowMapper;
import com.hbue.ordering.product.messaging.ProductSearchOutboxWriter;
import com.hbue.ordering.product.model.DiningWindowDO;
import com.hbue.ordering.product.service.impl.ProductAdministrationServiceImpl;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

/** 普通资料与独立图片保存的并发边界回归：省略图片字段必须真正不更新对应列。 */
class WindowGalleryUpdateTest {
    @Test void omittedImageFieldsCannotOverwriteConcurrentGallery() {
        DiningWindowMapper mapper = mock(DiningWindowMapper.class);
        DiningWindowDO window = new DiningWindowDO();
        window.setWindowId(9L); window.setImageFileKeys("[]");
        when(mapper.selectById(9L)).thenReturn(window);
        doAnswer(call -> {
            UpdateWrapper<?> update = call.getArgument(1);
            // 检查实际MP更新字段，避免仅断言Service返回值而漏掉无意写回旧图。
            assertFalse(update.getSqlSet().contains("image_file_key"));
            return 1;
        }).when(mapper).update(isNull(), any(UpdateWrapper.class));
        ProductAdministrationServiceImpl service = new ProductAdministrationServiceImpl(mapper, null, null,
                mock(DataScopeService.class), mock(ProductMenuCacheService.class),
                mock(ProductBloomFilterManager.class), null, mock(ProductSearchOutboxWriter.class));
        WindowSaveRequest request = new WindowSaveRequest();
        request.setRestaurantId(1L); request.setWindowName("更新名称");
        service.updateWindow(7L, 9L, request);
        verify(mapper).update(isNull(), any(UpdateWrapper.class));
    }
}

package com.hbue.ordering.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.core.scope.DataScopeService;
import com.hbue.ordering.common.core.scope.UserScopeSnapshot;
import com.hbue.ordering.common.web.media.ManagedImageClient;
import com.hbue.ordering.product.dto.request.RestaurantSaveRequest;
import com.hbue.ordering.product.cache.ProductMenuCacheService;
import com.hbue.ordering.product.mapper.DiningWindowMapper;
import com.hbue.ordering.product.mapper.RestaurantMapper;
import com.hbue.ordering.product.model.RestaurantDO;
import com.hbue.ordering.product.service.RestaurantAdministrationService;
import com.hbue.ordering.product.vo.RestaurantAdminVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * 餐厅管理服务测试：平台口径与餐厅经理口径的边界。
 */
@ExtendWith(MockitoExtension.class)
class RestaurantAdministrationServiceImplTest {

    /**
     * 测试用操作者用户 ID。
     */
    private static final Long OPERATOR_ID = 77L;

    /**
     * 测试用餐厅 ID。
     */
    private static final Long RESTAURANT_ID = 1L;

    @Mock
    private RestaurantMapper restaurantMapper;

    @Mock
    private DiningWindowMapper diningWindowMapper;

    @Mock
    private DataScopeService dataScopeService;

    @Mock
    private ProductMenuCacheService menuCacheService;

    @Mock
    private ManagedImageClient images;

    @Test
    void shouldRejectCreateWithoutPlatformScope() {
        when(dataScopeService.loadScopes(OPERATOR_ID)).thenReturn(restaurantScope());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service().createRestaurant(
                        OPERATOR_ID,
                        request("R0002", 30)
                )
        );

        assertEquals(
                CommonErrorCode.FORBIDDEN.code(),
                exception.getErrorCode().code()
        );
    }

    @Test
    void shouldDefaultSortOrderToZeroWhenCreating() {
        when(dataScopeService.loadScopes(OPERATOR_ID)).thenReturn(platformScope());
        when(restaurantMapper.insert(any(RestaurantDO.class)))
                .thenAnswer(invocation -> {
                    RestaurantDO saved = invocation.getArgument(0);
                    saved.setRestaurantId(9L);
                    return 1;
                });
        when(restaurantMapper.selectById(9L)).thenReturn(null);

        RestaurantAdminVO result = service().createRestaurant(
                OPERATOR_ID,
                request("R0002", null)
        );

        assertEquals(9L, result.getRestaurantId());
        assertEquals(0, result.getSortOrder().intValue());
        assertEquals(1, result.getStatus().intValue());
    }

    @Test
    void shouldMapDuplicateRestaurantCodeToConflict() {
        when(dataScopeService.loadScopes(OPERATOR_ID)).thenReturn(platformScope());
        when(restaurantMapper.insert(any(RestaurantDO.class)))
                .thenThrow(new DuplicateKeyException("duplicate restaurant code"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service().createRestaurant(
                        OPERATOR_ID,
                        request("R0001", 30)
                )
        );

        assertEquals(
                CommonErrorCode.CONFLICT.code(),
                exception.getErrorCode().code()
        );
    }

    @Test
    void shouldRejectSortOrderChangeByRestaurantManager() {
        when(restaurantMapper.selectById(RESTAURANT_ID))
                .thenReturn(restaurant(RESTAURANT_ID, "R0001", 10));
        when(dataScopeService.loadScopes(OPERATOR_ID)).thenReturn(restaurantScope());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service().updateRestaurant(
                        OPERATOR_ID,
                        RESTAURANT_ID,
                        request("R0001", 5)
                )
        );

        assertEquals(
                CommonErrorCode.FORBIDDEN.code(),
                exception.getErrorCode().code()
        );
    }

    @Test
    void shouldRejectRestaurantCodeChangeByRestaurantManager() {
        when(restaurantMapper.selectById(RESTAURANT_ID))
                .thenReturn(restaurant(RESTAURANT_ID, "R0001", 10));
        when(dataScopeService.loadScopes(OPERATOR_ID)).thenReturn(restaurantScope());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service().updateRestaurant(
                        OPERATOR_ID,
                        RESTAURANT_ID,
                        request("R0009", null)
                )
        );

        assertEquals(
                CommonErrorCode.FORBIDDEN.code(),
                exception.getErrorCode().code()
        );
    }

    @Test
    void shouldAllowRestaurantManagerToUpdateDisplayInfo() {
        when(restaurantMapper.selectById(RESTAURANT_ID))
                .thenReturn(restaurant(RESTAURANT_ID, "R0001", 10));
        when(dataScopeService.loadScopes(OPERATOR_ID)).thenReturn(restaurantScope());
        when(restaurantMapper.update(isNull(), any(UpdateWrapper.class)))
                .thenReturn(1);
        RestaurantSaveRequest request = request("R0001", null);
        request.setPhone("027-88886666");
        request.setNotice("今日供应热干面");

        RestaurantAdminVO result = service().updateRestaurant(
                OPERATOR_ID,
                RESTAURANT_ID,
                request
        );

        assertNotNull(result);
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<UpdateWrapper<RestaurantDO>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(restaurantMapper).update(isNull(), captor.capture());
        assertTrue(
                captor.getValue().getParamNameValuePairs()
                        .containsValue("027-88886666")
        );
        assertTrue(
                captor.getValue().getParamNameValuePairs().containsValue(10)
        );
    }

    @Test
    void shouldRejectUpdateWhenRestaurantOutOfScope() {
        when(restaurantMapper.selectById(5L))
                .thenReturn(restaurant(5L, "R0005", 0));
        doThrow(new BusinessException(CommonErrorCode.FORBIDDEN, "无权操作该餐厅"))
                .when(dataScopeService)
                .checkRestaurantAccess(OPERATOR_ID, 5L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service().updateRestaurant(
                        OPERATOR_ID,
                        5L,
                        request("R0005", null)
                )
        );

        assertEquals(
                CommonErrorCode.FORBIDDEN.code(),
                exception.getErrorCode().code()
        );
    }

    @Test
    void shouldRejectDisableWhenActiveWindowsExist() {
        when(dataScopeService.loadScopes(OPERATOR_ID)).thenReturn(platformScope());
        when(restaurantMapper.selectById(RESTAURANT_ID))
                .thenReturn(restaurant(RESTAURANT_ID, "R0001", 0));
        when(diningWindowMapper.selectCount(any(QueryWrapper.class)))
                .thenReturn(2L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service().disableRestaurant(OPERATOR_ID, RESTAURANT_ID)
        );

        assertEquals(
                CommonErrorCode.CONFLICT.code(),
                exception.getErrorCode().code()
        );
    }

    /**
     * 总经理调整餐厅展示排序后必须失效启用窗口列表缓存，否则学生端窗口顺序会延迟几分钟才生效。
     */
    @Test
    void shouldEvictActiveWindowsCacheWhenSortOrderChanges() {
        when(restaurantMapper.selectById(RESTAURANT_ID))
                .thenReturn(restaurant(RESTAURANT_ID, "R0001", 10));
        when(dataScopeService.loadScopes(OPERATOR_ID)).thenReturn(platformScope());
        when(restaurantMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        service().updateRestaurant(OPERATOR_ID, RESTAURANT_ID, request("R0001", 5));

        verify(menuCacheService).evictActiveWindows();
    }

    /**
     * 餐厅图片绑定只更新图片列，并要求文件归属当前操作者与餐厅业务类型。
     */
    @Test
    void shouldBindRestaurantImageWithinScope() {
        String fileKey = "a".repeat(32);
        when(restaurantMapper.selectById(RESTAURANT_ID))
                .thenReturn(restaurant(RESTAURANT_ID, "R0001", 10));
        when(images.requireImageKey(fileKey, OPERATOR_ID, "RESTAURANT_IMAGE", RESTAURANT_ID))
                .thenReturn(fileKey);
        when(restaurantMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        RestaurantAdminVO result = service().updateRestaurantImage(
                OPERATOR_ID,
                RESTAURANT_ID,
                fileKey
        );

        assertNotNull(result);
        verify(dataScopeService).checkRestaurantAccess(OPERATOR_ID, RESTAURANT_ID);
        verify(images).requireImageKey(
                fileKey,
                OPERATOR_ID,
                "RESTAURANT_IMAGE",
                RESTAURANT_ID
        );
    }

    /**
     * 餐厅越权时必须在写入前返回 403，不能只依赖前端隐藏入口。
     */
    @Test
    void shouldRejectRestaurantImageOutsideScope() {
        when(restaurantMapper.selectById(5L))
                .thenReturn(restaurant(5L, "R0005", 0));
        doThrow(new BusinessException(CommonErrorCode.FORBIDDEN, "无权操作该餐厅"))
                .when(dataScopeService)
                .checkRestaurantAccess(OPERATOR_ID, 5L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service().updateRestaurantImage(OPERATOR_ID, 5L, "a".repeat(32))
        );

        assertEquals(CommonErrorCode.FORBIDDEN.code(), exception.getErrorCode().code());
        verify(restaurantMapper, never()).update(any(), any());
    }

    private RestaurantAdministrationService service() {
        return new RestaurantAdministrationServiceImpl(
                restaurantMapper,
                diningWindowMapper,
                dataScopeService,
                images,
                org.mockito.Mockito.mock(com.hbue.ordering.product.messaging.ProductSearchOutboxWriter.class),
                menuCacheService
        );
    }

    private UserScopeSnapshot platformScope() {
        return new UserScopeSnapshot(
                OPERATOR_ID,
                true,
                Collections.emptySet(),
                Collections.emptySet()
        );
    }

    private UserScopeSnapshot restaurantScope() {
        return new UserScopeSnapshot(
                OPERATOR_ID,
                false,
                Collections.emptySet(),
                Collections.singleton(RESTAURANT_ID)
        );
    }

    private RestaurantDO restaurant(Long id, String code, Integer sortOrder) {
        RestaurantDO restaurant = new RestaurantDO();
        restaurant.setRestaurantId(id);
        restaurant.setRestaurantCode(code);
        restaurant.setRestaurantName("第一食堂");
        restaurant.setLocation("一楼");
        restaurant.setSortOrder(sortOrder);
        restaurant.setStatus(1);
        return restaurant;
    }

    private RestaurantSaveRequest request(String code, Integer sortOrder) {
        RestaurantSaveRequest request = new RestaurantSaveRequest();
        request.setRestaurantCode(code);
        request.setRestaurantName("第一食堂");
        request.setLocation("一楼");
        request.setSortOrder(sortOrder);
        return request;
    }
}

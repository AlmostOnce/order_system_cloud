package com.hbue.ordering.product.service.impl;

import com.hbue.ordering.common.web.media.FileReferences;
import com.hbue.ordering.common.web.media.ManagedImageClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.core.scope.DataScopeService;
import com.hbue.ordering.common.core.scope.UserScopeSnapshot;
import com.hbue.ordering.product.dto.request.RestaurantSaveRequest;
import com.hbue.ordering.product.mapper.DiningWindowMapper;
import com.hbue.ordering.product.mapper.RestaurantMapper;
import com.hbue.ordering.product.model.DiningWindowDO;
import com.hbue.ordering.product.model.RestaurantDO;
import com.hbue.ordering.product.messaging.ProductSearchOutboxWriter;
import com.hbue.ordering.product.service.RestaurantAdministrationService;
import com.hbue.ordering.product.vo.RestaurantAdminVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 餐厅（食堂）管理业务实现。
 *
 * <p>餐厅的编码与展示排序属平台口径，只有持全平台范围的总经理可以新建、停用
 * 与调整；餐厅经理只能维护自己餐厅的名称、位置、电话、门面图与公告。
 * 餐厅属于公开资源，越权统一返回 403（不隐藏存在性）。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Service
public class RestaurantAdministrationServiceImpl
        implements RestaurantAdministrationService {

    /** 启用状态值。 */
    private static final int ACTIVE_STATUS = 1;

    /** 停用状态值。 */
    private static final int INACTIVE_STATUS = 0;

    /** 餐厅数据 Mapper。 */
    private final RestaurantMapper restaurantMapper;

    /** 窗口数据 Mapper，用于停用前的档口占用检查。 */
    private final DiningWindowMapper diningWindowMapper;

    /** 数据范围判定服务。 */
    private final DataScopeService dataScopeService;
    private final ManagedImageClient images;
    private final ProductSearchOutboxWriter searchOutbox;

    /**
     * 创建餐厅管理服务。
     *
     * @param restaurantMapper 餐厅数据 Mapper
     * @param diningWindowMapper 窗口数据 Mapper
     * @param dataScopeService 数据范围判定服务
     */
    public RestaurantAdministrationServiceImpl(
            RestaurantMapper restaurantMapper,
            DiningWindowMapper diningWindowMapper,
            DataScopeService dataScopeService,
            ManagedImageClient images,
            ProductSearchOutboxWriter searchOutbox
    ) {
        this.restaurantMapper = restaurantMapper;
        this.diningWindowMapper = diningWindowMapper;
        this.dataScopeService = dataScopeService;
        this.images = images;
        this.searchOutbox = searchOutbox;
    }

    /** {@inheritDoc} */
    @Override
    public List<RestaurantAdminVO> listRestaurants(Long operatorId) {
        UserScopeSnapshot scopes = dataScopeService.loadScopes(operatorId);
        List<RestaurantDO> restaurants = restaurantMapper.selectList(
                new QueryWrapper<RestaurantDO>()
                        .orderByAsc("sort_order")
                        .orderByAsc("restaurant_id")
        );

        // 只返回操作者范围内的餐厅；全平台范围返回全部。
        List<RestaurantAdminVO> results = new ArrayList<>();
        for (RestaurantDO restaurant : restaurants) {
            if (scopes.coversRestaurant(restaurant.getRestaurantId())) {
                results.add(toRestaurantVO(restaurant));
            }
        }
        return results;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public RestaurantAdminVO createRestaurant(
            Long operatorId,
            RestaurantSaveRequest request
    ) {
        requireRequest(request);
        requirePlatformScope(operatorId, "新建餐厅");

        RestaurantDO restaurant = new RestaurantDO();
        restaurant.setRestaurantCode(request.getRestaurantCode());
        restaurant.setRestaurantName(request.getRestaurantName());
        restaurant.setLocation(request.getLocation());
        restaurant.setPhone(request.getPhone());
        restaurant.setImageFileKey(images.resolveUpdateKey(request.getImageFileKey(), null, operatorId, "RESTAURANT_IMAGE", null));
        restaurant.setNotice(request.getNotice());
        restaurant.setSortOrder(resolveSortOrder(request.getSortOrder(), 0));
        restaurant.setStatus(ACTIVE_STATUS);
        try {
            restaurantMapper.insert(restaurant);
        } catch (DuplicateKeyException exception) {
            throw conflict("餐厅编码已存在");
        }
        RestaurantDO persisted = restaurantMapper.selectById(
                restaurant.getRestaurantId()
        );
        return toRestaurantVO(persisted == null ? restaurant : persisted);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public RestaurantAdminVO updateRestaurant(
            Long operatorId,
            Long restaurantId,
            RestaurantSaveRequest request
    ) {
        validateId(restaurantId);
        requireRequest(request);
        RestaurantDO existing = requireRestaurant(restaurantId);
        dataScopeService.checkRestaurantAccess(operatorId, restaurantId);
        List<DiningWindowDO> attachedWindows = diningWindowMapper.selectList(new QueryWrapper<DiningWindowDO>().eq("restaurant_id", restaurantId));

        UserScopeSnapshot scopes = dataScopeService.loadScopes(operatorId);
        if (!scopes.isPlatformScope()) {
            // 餐厅经理只能维护本餐厅的展示信息，编码与展示排序属平台口径。
            if (!existing.getRestaurantCode().equals(request.getRestaurantCode())) {
                throw forbidden("餐厅编码只能由总经理修改");
            }
            if (!sameSortOrder(request.getSortOrder(), existing.getSortOrder())) {
                throw forbidden("展示排序只能由总经理调整");
            }
        }

        UpdateWrapper<RestaurantDO> update = new UpdateWrapper<>();
        update.eq("restaurant_id", restaurantId)
                .set("restaurant_code", request.getRestaurantCode())
                .set("restaurant_name", request.getRestaurantName())
                .set("location", request.getLocation())
                .set("phone", request.getPhone())
                .set(request.getImageFileKey() != null, "image_file_key", images.resolveUpdateKey(
                        request.getImageFileKey(), existing.getImageFileKey(), operatorId, "RESTAURANT_IMAGE", restaurantId))
                .set("notice", request.getNotice())
                .set(
                        "sort_order",
                        resolveSortOrder(
                                request.getSortOrder(),
                                existing.getSortOrder()
                        )
                );
        try {
            restaurantMapper.update(null, update);
        } catch (DuplicateKeyException exception) {
            throw conflict("餐厅编码已存在");
        }
        attachedWindows.forEach(window -> searchOutbox.recordWindow(window.getWindowId()));
        return toRestaurantVO(requireRestaurant(restaurantId));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void disableRestaurant(Long operatorId, Long restaurantId) {
        validateId(restaurantId);
        requirePlatformScope(operatorId, "停用餐厅");
        RestaurantDO existing = requireRestaurant(restaurantId);
        List<DiningWindowDO> attachedWindows = diningWindowMapper.selectList(new QueryWrapper<DiningWindowDO>().eq("restaurant_id", restaurantId));
        if (Integer.valueOf(INACTIVE_STATUS).equals(existing.getStatus())) {
            return;
        }

        // 餐厅下还有启用档口时不允许停用，避免出现挂在停用餐厅下的可下单档口。
        Long activeWindows = diningWindowMapper.selectCount(
                new QueryWrapper<DiningWindowDO>()
                        .eq("restaurant_id", restaurantId)
                        .eq("status", ACTIVE_STATUS)
        );
        if (activeWindows != null && activeWindows > 0) {
            throw conflict("餐厅下仍有启用的档口，请先停用档口");
        }

        UpdateWrapper<RestaurantDO> update = new UpdateWrapper<>();
        update.eq("restaurant_id", restaurantId).set("status", INACTIVE_STATUS);
        restaurantMapper.update(null, update);
        attachedWindows.forEach(window -> searchOutbox.recordWindow(window.getWindowId()));
    }

    /**
     * 校验操作者是否持有全平台范围。
     *
     * <p>展示排序、新建与停用属平台口径，只有总经理（全平台范围）可以操作。</p>
     *
     * @param operatorId 当前登录操作者 ID
     * @param action 操作名称
     */
    private void requirePlatformScope(Long operatorId, String action) {
        UserScopeSnapshot scopes = dataScopeService.loadScopes(operatorId);
        if (!scopes.isPlatformScope()) {
            throw forbidden("只有全平台范围的总经理可以" + action);
        }
    }

    /**
     * 解析展示排序：请求留空时沿用兜底值。
     *
     * @param requested 请求中的展示排序
     * @param fallback 兜底值
     * @return 最终写入的展示排序
     */
    private Integer resolveSortOrder(Integer requested, Integer fallback) {
        if (requested != null) {
            return requested;
        }
        return fallback == null ? 0 : fallback;
    }

    /**
     * 判断请求中的展示排序是否等同于原值；留空表示不变。
     *
     * @param requested 请求中的展示排序
     * @param existing 原展示排序
     * @return 未要求改动时返回 true
     */
    private boolean sameSortOrder(Integer requested, Integer existing) {
        if (requested == null) {
            return true;
        }
        if (existing == null) {
            return false;
        }
        return requested.equals(existing);
    }

    /**
     * 查询餐厅，不存在时返回统一 404 业务异常。
     *
     * @param restaurantId 餐厅 ID
     * @return 餐厅数据
     */
    private RestaurantDO requireRestaurant(Long restaurantId) {
        RestaurantDO restaurant = restaurantMapper.selectById(restaurantId);
        if (restaurant == null) {
            throw notFound("餐厅不存在");
        }
        return restaurant;
    }

    /**
     * 校验路径 ID 为正整数。
     *
     * @param id 路径 ID
     */
    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "餐厅 ID 必须为正整数"
            );
        }
    }

    /**
     * 校验请求对象非空。
     *
     * @param request 请求对象
     */
    private void requireRequest(Object request) {
        if (request == null) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "请求参数不能为空"
            );
        }
    }

    /**
     * 构造资源不存在异常。
     *
     * @param message 提示信息
     * @return 资源不存在异常
     */
    private BusinessException notFound(String message) {
        return new BusinessException(CommonErrorCode.NOT_FOUND, message);
    }

    /**
     * 构造唯一键冲突异常。
     *
     * @param message 提示信息
     * @return 请求冲突异常
     */
    private BusinessException conflict(String message) {
        return new BusinessException(CommonErrorCode.CONFLICT, message);
    }

    /**
     * 构造无权限异常。
     *
     * @param message 提示信息
     * @return 无权限异常
     */
    private BusinessException forbidden(String message) {
        return new BusinessException(CommonErrorCode.FORBIDDEN, message);
    }

    /**
     * 将餐厅持久化对象转换为管理返回对象。
     *
     * @param restaurant 餐厅数据
     * @return 餐厅管理视图
     */
    private RestaurantAdminVO toRestaurantVO(RestaurantDO restaurant) {
        return new RestaurantAdminVO(
                restaurant.getRestaurantId(),
                restaurant.getRestaurantCode(),
                restaurant.getRestaurantName(),
                restaurant.getLocation(),
                restaurant.getPhone(),
                FileReferences.url(restaurant.getImageFileKey()),
                restaurant.getNotice(),
                restaurant.getSortOrder(),
                restaurant.getStatus(),
                restaurant.getCreatedAt(),
                restaurant.getUpdatedAt()
        );
    }
}

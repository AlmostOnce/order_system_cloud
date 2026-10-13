package com.hbue.ordering.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.core.scope.DataScopeService;
import com.hbue.ordering.common.core.scope.UserScopeSnapshot;
import com.hbue.ordering.product.dto.request.ProductCategorySaveRequest;
import com.hbue.ordering.product.dto.request.ProductSaveRequest;
import com.hbue.ordering.product.dto.request.WindowSaveRequest;
import com.hbue.ordering.product.mapper.DiningWindowMapper;
import com.hbue.ordering.product.mapper.ProductCategoryMapper;
import com.hbue.ordering.product.mapper.ProductMapper;
import com.hbue.ordering.product.model.DiningWindowDO;
import com.hbue.ordering.product.model.ProductCategoryDO;
import com.hbue.ordering.product.model.ProductDO;
import com.hbue.ordering.product.service.ProductAdministrationService;
import com.hbue.ordering.product.vo.ProductAdminVO;
import com.hbue.ordering.product.vo.ProductCategoryAdminVO;
import com.hbue.ordering.product.vo.WindowAdminVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 窗口、分类和菜品管理业务实现。
 *
 * <p>所有方法在执行前都按 {@code operatorId} 做数据范围校验：
 * 档口与菜单属于公开资源，越权统一返回 403（不隐藏存在性）。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
@Service
public class ProductAdministrationServiceImpl
        implements ProductAdministrationService {

    /** 启用状态值。 */
    private static final int ACTIVE_STATUS = 1;

    /** 在售状态值。 */
    private static final int ON_SALE_STATUS = 1;

    /** 窗口数据 Mapper。 */
    private final DiningWindowMapper diningWindowMapper;

    /** 分类数据 Mapper。 */
    private final ProductCategoryMapper productCategoryMapper;

    /** 菜品数据 Mapper。 */
    private final ProductMapper productMapper;

    /** 数据范围判定服务。 */
    private final DataScopeService dataScopeService;

    /**
     * 创建目录管理服务。
     *
     * @param diningWindowMapper 窗口数据 Mapper
     * @param productCategoryMapper 分类数据 Mapper
     * @param productMapper 菜品数据 Mapper
     * @param dataScopeService 数据范围判定服务
     */
    public ProductAdministrationServiceImpl(
            DiningWindowMapper diningWindowMapper,
            ProductCategoryMapper productCategoryMapper,
            ProductMapper productMapper,
            DataScopeService dataScopeService
    ) {
        this.diningWindowMapper = diningWindowMapper;
        this.productCategoryMapper = productCategoryMapper;
        this.productMapper = productMapper;
        this.dataScopeService = dataScopeService;
    }

    /** {@inheritDoc} */
    @Override
    public List<WindowAdminVO> listWindows(Long operatorId) {
        List<DiningWindowDO> windows = diningWindowMapper.selectList(
                new QueryWrapper<DiningWindowDO>().orderByAsc("window_id")
        );

        // 只返回操作者范围内的档口；全平台范围返回全部。
        UserScopeSnapshot scopes = dataScopeService.loadScopes(operatorId);
        List<WindowAdminVO> results = new ArrayList<>();
        for (DiningWindowDO window : windows) {
            if (withinScope(scopes, window)) {
                results.add(toWindowVO(window));
            }
        }
        return results;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public WindowAdminVO createWindow(
            Long operatorId,
            WindowSaveRequest request
    ) {
        requireRequest(request);

        // 只能在自己管辖的餐厅下新建档口；越权返回 403。
        dataScopeService.checkRestaurantAccess(
                operatorId,
                request.getRestaurantId()
        );

        DiningWindowDO window = new DiningWindowDO();
        window.setRestaurantId(request.getRestaurantId());
        window.setWindowCode(request.getWindowCode());
        window.setWindowName(request.getWindowName());
        window.setLocation(request.getLocation());
        window.setImageUrl(request.getImageUrl());
        window.setStatus(request.getStatus());
        window.setIsOpen(request.getIsOpen());
        try {
            diningWindowMapper.insert(window);
        } catch (DuplicateKeyException exception) {
            throw conflict("窗口编码已存在");
        }
        DiningWindowDO persistedWindow = diningWindowMapper.selectById(
                window.getWindowId()
        );
        return toWindowVO(persistedWindow == null ? window : persistedWindow);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public WindowAdminVO updateWindow(
            Long operatorId,
            Long windowId,
            WindowSaveRequest request
    ) {
        validateId(windowId, "窗口");
        requireRequest(request);

        // 目标档口必须落在操作者范围内，改挂餐厅也不能越界。
        dataScopeService.checkWindowAccess(operatorId, windowId);
        dataScopeService.checkRestaurantAccess(
                operatorId,
                request.getRestaurantId()
        );
        requireWindow(windowId);

        UpdateWrapper<DiningWindowDO> update = new UpdateWrapper<>();
        update.eq("window_id", windowId)
                .set("restaurant_id", request.getRestaurantId())
                .set("window_code", request.getWindowCode())
                .set("window_name", request.getWindowName())
                .set("location", request.getLocation())
                .set("image_url", request.getImageUrl())
                .set("status", request.getStatus())
                .set("is_open", request.getIsOpen());
        try {
            diningWindowMapper.update(null, update);
        } catch (DuplicateKeyException exception) {
            throw conflict("窗口编码已存在");
        }
        return toWindowVO(requireWindow(windowId));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void disableWindow(Long operatorId, Long windowId) {
        validateId(windowId, "窗口");
        dataScopeService.checkWindowAccess(operatorId, windowId);
        DiningWindowDO window = requireWindow(windowId);
        if (Integer.valueOf(0).equals(window.getStatus())) {
            return;
        }
        UpdateWrapper<DiningWindowDO> update = new UpdateWrapper<>();
        update.eq("window_id", windowId).set("status", 0);
        diningWindowMapper.update(null, update);
    }

    /** {@inheritDoc} */
    @Override
    public List<ProductCategoryAdminVO> listCategories(
            Long operatorId,
            Long windowId
    ) {
        validateId(windowId, "窗口");
        dataScopeService.checkWindowAccess(operatorId, windowId);
        requireWindow(windowId);
        List<ProductCategoryDO> categories = productCategoryMapper.selectList(
                new QueryWrapper<ProductCategoryDO>()
                        .eq("window_id", windowId)
                        .orderByAsc("sort_order")
                        .orderByAsc("category_id")
        );
        List<ProductCategoryAdminVO> results = new ArrayList<>();
        for (ProductCategoryDO category : categories) {
            results.add(toCategoryVO(category));
        }
        return results;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ProductCategoryAdminVO createCategory(
            Long operatorId,
            Long windowId,
            ProductCategorySaveRequest request
    ) {
        validateId(windowId, "窗口");
        requireRequest(request);
        dataScopeService.checkWindowAccess(operatorId, windowId);
        requireWindow(windowId);
        ProductCategoryDO category = new ProductCategoryDO();
        category.setWindowId(windowId);
        category.setCategoryName(request.getCategoryName());
        category.setSortOrder(request.getSortOrder());
        category.setStatus(request.getStatus());
        try {
            productCategoryMapper.insert(category);
        } catch (DuplicateKeyException exception) {
            throw conflict("该窗口下已存在同名分类");
        }
        ProductCategoryDO persistedCategory = productCategoryMapper.selectById(
                category.getCategoryId()
        );
        return toCategoryVO(
                persistedCategory == null ? category : persistedCategory
        );
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ProductCategoryAdminVO updateCategory(
            Long operatorId,
            Long categoryId,
            ProductCategorySaveRequest request
    ) {
        validateId(categoryId, "分类");
        requireRequest(request);
        ProductCategoryDO existingCategory = requireCategory(categoryId);
        dataScopeService.checkWindowAccess(
                operatorId,
                existingCategory.getWindowId()
        );

        UpdateWrapper<ProductCategoryDO> update = new UpdateWrapper<>();
        update.eq("category_id", categoryId)
                .set("category_name", request.getCategoryName())
                .set("sort_order", request.getSortOrder())
                .set("status", request.getStatus());
        try {
            productCategoryMapper.update(null, update);
        } catch (DuplicateKeyException exception) {
            throw conflict("该窗口下已存在同名分类");
        }
        return toCategoryVO(requireCategory(categoryId));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void disableCategory(Long operatorId, Long categoryId) {
        validateId(categoryId, "分类");
        ProductCategoryDO category = requireCategory(categoryId);
        dataScopeService.checkWindowAccess(operatorId, category.getWindowId());
        if (Integer.valueOf(0).equals(category.getStatus())) {
            return;
        }
        UpdateWrapper<ProductCategoryDO> update = new UpdateWrapper<>();
        update.eq("category_id", categoryId).set("status", 0);
        productCategoryMapper.update(null, update);
    }

    /** {@inheritDoc} */
    @Override
    public List<ProductAdminVO> listProducts(Long operatorId, Long windowId) {
        validateId(windowId, "窗口");
        dataScopeService.checkWindowAccess(operatorId, windowId);
        requireWindow(windowId);
        List<ProductDO> products = productMapper.selectList(
                new QueryWrapper<ProductDO>()
                        .eq("window_id", windowId)
                        .orderByAsc("category_id")
                        .orderByAsc("sort_order")
                        .orderByAsc("product_id")
        );
        List<ProductAdminVO> results = new ArrayList<>();
        for (ProductDO product : products) {
            results.add(toProductVO(product));
        }
        return results;
    }

    /** {@inheritDoc} */
    @Override
    public ProductAdminVO getProduct(Long operatorId, Long productId) {
        validateId(productId, "菜品");
        ProductDO product = requireProduct(productId);
        dataScopeService.checkWindowAccess(operatorId, product.getWindowId());
        return toProductVO(product);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ProductAdminVO createProduct(
            Long operatorId,
            Long windowId,
            ProductSaveRequest request
    ) {
        validateId(windowId, "窗口");
        requireRequest(request);
        dataScopeService.checkWindowAccess(operatorId, windowId);
        requireWindow(windowId);
        validateCategoryForProduct(
                windowId,
                request.getCategoryId(),
                request.getSaleStatus()
        );

        ProductDO product = new ProductDO();
        product.setWindowId(windowId);
        copyProductFields(request, product);
        try {
            productMapper.insert(product);
        } catch (DuplicateKeyException exception) {
            throw conflict("该窗口下已存在相同菜品编码");
        }
        ProductDO persistedProduct = productMapper.selectById(
                product.getProductId()
        );
        return toProductVO(persistedProduct == null ? product : persistedProduct);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ProductAdminVO updateProduct(
            Long operatorId,
            Long productId,
            ProductSaveRequest request
    ) {
        validateId(productId, "菜品");
        requireRequest(request);
        ProductDO existingProduct = requireProduct(productId);
        dataScopeService.checkWindowAccess(
                operatorId,
                existingProduct.getWindowId()
        );
        validateCategoryForProduct(
                existingProduct.getWindowId(),
                request.getCategoryId(),
                request.getSaleStatus()
        );

        UpdateWrapper<ProductDO> update = new UpdateWrapper<>();
        update.eq("product_id", productId)
                .set("category_id", request.getCategoryId())
                .set("product_code", request.getProductCode())
                .set("product_name", request.getProductName())
                .set("description", request.getDescription())
                .set("image_url", request.getImageUrl())
                .set("price", request.getPrice())
                .set("sale_status", request.getSaleStatus())
                .set("sort_order", request.getSortOrder());
        try {
            productMapper.update(null, update);
        } catch (DuplicateKeyException exception) {
            throw conflict("该窗口下已存在相同菜品编码");
        }
        return toProductVO(requireProduct(productId));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void disableProduct(Long operatorId, Long productId) {
        validateId(productId, "菜品");
        ProductDO product = requireProduct(productId);
        dataScopeService.checkWindowAccess(operatorId, product.getWindowId());
        if (Integer.valueOf(0).equals(product.getSaleStatus())) {
            return;
        }
        UpdateWrapper<ProductDO> update = new UpdateWrapper<>();
        update.eq("product_id", productId).set("sale_status", 0);
        productMapper.update(null, update);
    }

    /**
     * 判断档口是否落在给定范围快照内。
     *
     * <p>主管档口、所属餐厅、全平台范围均视为落在范围内。</p>
     *
     * @param scopes 范围快照
     * @param window 档口数据
     * @return 落在范围内时返回 true
     */
    private boolean withinScope(
            UserScopeSnapshot scopes,
            DiningWindowDO window
    ) {
        return scopes.coversWindow(window.getWindowId())
                || scopes.coversRestaurant(window.getRestaurantId());
    }

    /**
     * 校验商品分类存在、属于目标窗口，且在售菜品只能使用启用分类。
     *
     * @param windowId 目标窗口 ID
     * @param categoryId 分类 ID
     * @param saleStatus 菜品售卖状态
     */
    private void validateCategoryForProduct(
            Long windowId,
            Long categoryId,
            Integer saleStatus
    ) {
        if (categoryId == null) {
            return;
        }
        ProductCategoryDO category = requireCategory(categoryId);
        if (!windowId.equals(category.getWindowId())) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "菜品分类必须属于目标窗口"
            );
        }
        if (Integer.valueOf(ON_SALE_STATUS).equals(saleStatus)
                && !Integer.valueOf(ACTIVE_STATUS).equals(category.getStatus())) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "在售菜品必须使用启用状态的分类"
            );
        }
    }

    /**
     * 按请求字段覆盖菜品对象。
     *
     * @param request 菜品字段
     * @param product 菜品持久化对象
     */
    private void copyProductFields(
            ProductSaveRequest request,
            ProductDO product
    ) {
        product.setCategoryId(request.getCategoryId());
        product.setProductCode(request.getProductCode());
        product.setProductName(request.getProductName());
        product.setDescription(request.getDescription());
        product.setImageUrl(request.getImageUrl());
        product.setPrice(request.getPrice());
        product.setSaleStatus(request.getSaleStatus());
        product.setSortOrder(request.getSortOrder());
    }

    /**
     * 查询窗口，不存在时返回统一 404 业务异常。
     *
     * @param windowId 窗口 ID
     * @return 窗口数据
     */
    private DiningWindowDO requireWindow(Long windowId) {
        DiningWindowDO window = diningWindowMapper.selectById(windowId);
        if (window == null) {
            throw notFound("窗口不存在");
        }
        return window;
    }

    /**
     * 查询分类，不存在时返回统一 404 业务异常。
     *
     * @param categoryId 分类 ID
     * @return 分类数据
     */
    private ProductCategoryDO requireCategory(Long categoryId) {
        ProductCategoryDO category = productCategoryMapper.selectById(categoryId);
        if (category == null) {
            throw notFound("分类不存在");
        }
        return category;
    }

    /**
     * 查询菜品，不存在时返回统一 404 业务异常。
     *
     * @param productId 菜品 ID
     * @return 菜品数据
     */
    private ProductDO requireProduct(Long productId) {
        ProductDO product = productMapper.selectById(productId);
        if (product == null) {
            throw notFound("菜品不存在");
        }
        return product;
    }

    /**
     * 校验路径 ID 为正整数。
     *
     * @param id 路径 ID
     * @param resourceName 资源名称
     */
    private void validateId(Long id, String resourceName) {
        if (id == null || id <= 0) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    resourceName + " ID 必须为正整数"
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
     * 将窗口持久化对象转换为管理返回对象。
     *
     * @param window 窗口数据
     * @return 窗口管理视图
     */
    private WindowAdminVO toWindowVO(DiningWindowDO window) {
        return new WindowAdminVO(
                window.getWindowId(),
                window.getRestaurantId(),
                window.getWindowCode(),
                window.getWindowName(),
                window.getLocation(),
                window.getImageUrl(),
                window.getStatus(),
                window.getIsOpen(),
                window.getCreatedAt(),
                window.getUpdatedAt()
        );
    }

    /**
     * 将分类持久化对象转换为管理返回对象。
     *
     * @param category 分类数据
     * @return 分类管理视图
     */
    private ProductCategoryAdminVO toCategoryVO(ProductCategoryDO category) {
        return new ProductCategoryAdminVO(
                category.getCategoryId(),
                category.getWindowId(),
                category.getCategoryName(),
                category.getSortOrder(),
                category.getStatus(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }

    /**
     * 将菜品持久化对象转换为管理返回对象。
     *
     * @param product 菜品数据
     * @return 菜品管理视图
     */
    private ProductAdminVO toProductVO(ProductDO product) {
        return new ProductAdminVO(
                product.getProductId(),
                product.getWindowId(),
                product.getCategoryId(),
                product.getProductCode(),
                product.getProductName(),
                product.getDescription(),
                product.getImageUrl(),
                product.getPrice(),
                product.getSaleStatus(),
                product.getSortOrder(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}

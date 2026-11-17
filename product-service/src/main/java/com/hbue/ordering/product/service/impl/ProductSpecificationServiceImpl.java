package com.hbue.ordering.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
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
import com.hbue.ordering.product.model.ProductSpecificationOptionDO;
import com.hbue.ordering.product.service.ProductSpecificationService;
import com.hbue.ordering.product.vo.ProductSpecificationGroupVO;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.util.*;

/**
 * 菜品规格管理与批量读取；完整替换在商品行锁下执行，防止并发管理请求交叉停用彼此的配置。
 * 组和选项只软停用；任何校验或Outbox写入失败都会回滚整次替换，历史订单仍依赖自身快照。
 */
@Service
public class ProductSpecificationServiceImpl implements ProductSpecificationService {
    /** 商品Mapper，用于读取归属及锁住单个菜品，串行化规格更新。 */
    private final ProductMapper products;
    /** 规格组Mapper，只按商品归属查询和软停用。 */
    private final ProductSpecificationGroupMapper groups;
    /** 选项Mapper，操作范围始终由目标商品的规格组限定。 */
    private final ProductSpecificationOptionMapper options;
    /** 复用现有窗口范围校验，管理越权返回403。 */
    private final DataScopeService scopes;
    /** 提交成功后清理窗口菜单，避免回滚或未提交值进入缓存。 */
    private final ProductMenuCacheService menus;
    /** 与规格修改同事务登记索引刷新，发布失败由现有Outbox重试。 */
    private final ProductSearchOutboxWriter outbox;

    /** 注入现有商品、权限、缓存和Outbox能力，不引入第二套持久化框架。 */
    public ProductSpecificationServiceImpl(ProductMapper products, ProductSpecificationGroupMapper groups,
            ProductSpecificationOptionMapper options, DataScopeService scopes,
            ProductMenuCacheService menus, ProductSearchOutboxWriter outbox) {
        this.products = products;
        this.groups = groups;
        this.options = options;
        this.scopes = scopes;
        this.menus = menus;
        this.outbox = outbox;
    }

    /** 管理读取先验证商品存在及所属窗口范围，再批量查询当前启用配置。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ProductSpecificationGroupVO> get(Long operatorId, Long productId) {
        requireProduct(operatorId, productId);
        return loadActive(List.of(productId)).getOrDefault(productId, List.of());
    }

    /**
     * 全量替换只接受目标商品已有编号或无编号的新记录；不接受跨商品或跨组复用编号。
     * 先校验全部数据再停用旧记录；行锁保持到提交，失败不留下半份配置。
     */
    @Override
    @Transactional
    public List<ProductSpecificationGroupVO> replace(Long operatorId, Long productId,
            ProductSpecificationsSaveRequest request) {
        if (productId == null || productId <= 0) throw badRequest("菜品编号无效");
        // 首次读取即加行锁，避免先做快照读、等待并发提交后仍用旧快照读取上一版配置。
        ProductDO product = products.selectForSpecificationUpdate(productId);
        if (product == null) throw new BusinessException(CommonErrorCode.NOT_FOUND, "菜品不存在");
        scopes.checkWindowAccess(operatorId, product.getWindowId());
        List<ProductSpecificationGroupDO> oldGroups = groups.selectList(
                new QueryWrapper<ProductSpecificationGroupDO>().eq("product_id", productId));
        Map<Long, ProductSpecificationGroupDO> groupsById = new HashMap<>();
        oldGroups.forEach(group -> groupsById.put(group.getGroupId(), group));
        List<ProductSpecificationOptionDO> oldOptions = oldGroups.isEmpty() ? List.of() : options.selectList(
                new QueryWrapper<ProductSpecificationOptionDO>().in("group_id", groupsById.keySet()));
        Map<Long, ProductSpecificationOptionDO> optionsById = new HashMap<>();
        oldOptions.forEach(option -> optionsById.put(option.getOptionId(), option));
        validate(request, groupsById, optionsById);

        // 仅停用目标商品的旧配置，后续显式恢复本次保留的记录；禁止物理删除。
        if (!oldGroups.isEmpty()) {
            options.update(null, new UpdateWrapper<ProductSpecificationOptionDO>()
                    .in("group_id", groupsById.keySet()).set("status", 0));
            groups.update(null, new UpdateWrapper<ProductSpecificationGroupDO>()
                    .eq("product_id", productId).set("status", 0));
        }
        try {
            for (ProductSpecificationsSaveRequest.Group input : request.getGroups()) {
                ProductSpecificationGroupDO group = new ProductSpecificationGroupDO();
                group.setGroupId(input.getGroupId());
                group.setProductId(productId);
                group.setName(input.getName().strip());
                group.setRequired(input.getRequired());
                group.setSortOrder(input.getSortOrder());
                group.setStatus(1);
                if (group.getGroupId() == null) groups.insert(group);
                else groups.updateById(group);
                for (ProductSpecificationsSaveRequest.Option optionInput : input.getOptions()) {
                    ProductSpecificationOptionDO option = new ProductSpecificationOptionDO();
                    option.setOptionId(optionInput.getOptionId());
                    option.setGroupId(group.getGroupId());
                    option.setName(optionInput.getName().strip());
                    option.setAdditionalPrice(optionInput.getAdditionalPrice());
                    option.setSortOrder(optionInput.getSortOrder());
                    option.setStatus(1);
                    if (option.getOptionId() == null) options.insert(option);
                    else options.updateById(option);
                }
            }
        } catch (DuplicateKeyException exception) {
            // 数据库排序规则还会统一重音字符等名称，唯一键冲突同样回滚全部软停用和更新。
            throw new BusinessException(CommonErrorCode.CONFLICT, "启用规格组或选项名称重复");
        }
        outbox.recordProduct(product.getWindowId(), productId);
        // 与商品管理一致，事务提交之后才删菜单；回滚时不执行回调。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { menus.evictWindowMenu(product.getWindowId()); }
        });
        return loadActive(List.of(productId)).getOrDefault(productId, List.of());
    }

    /**
     * 两次批量查询组装启用规格，不随菜单商品数量增加查询次数；可重复读保证两次查询读取同一版本。
     * 显式拦截空集合避免MyBatis-Plus忽略空IN条件；组与选项均按排序和编号稳定排序。
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<Long, List<ProductSpecificationGroupVO>> loadActive(Collection<Long> productIds) {
        Map<Long, List<ProductSpecificationGroupVO>> result = new HashMap<>();
        if (productIds == null || productIds.isEmpty()) return result;
        List<ProductSpecificationGroupDO> activeGroups = groups.selectList(
                new QueryWrapper<ProductSpecificationGroupDO>().in("product_id", productIds)
                        .eq("status", 1).orderByAsc("sort_order", "group_id"));
        if (activeGroups.isEmpty()) return result;
        List<ProductSpecificationOptionDO> activeOptions = options.selectList(
                new QueryWrapper<ProductSpecificationOptionDO>()
                        .in("group_id", activeGroups.stream().map(ProductSpecificationGroupDO::getGroupId).toList())
                        .eq("status", 1).orderByAsc("sort_order", "option_id"));
        Map<Long, List<ProductSpecificationGroupVO.Option>> optionsByGroup = new HashMap<>();
        for (ProductSpecificationOptionDO option : activeOptions) {
            optionsByGroup.computeIfAbsent(option.getGroupId(), ignored -> new ArrayList<>()).add(
                    new ProductSpecificationGroupVO.Option(option.getOptionId(), option.getName(),
                            option.getAdditionalPrice(), option.getSortOrder()));
        }
        for (ProductSpecificationGroupDO group : activeGroups) {
            result.computeIfAbsent(group.getProductId(), ignored -> new ArrayList<>()).add(
                    new ProductSpecificationGroupVO(group.getGroupId(), group.getName(), group.getRequired(),
                            group.getSortOrder(), optionsByGroup.getOrDefault(group.getGroupId(), List.of())));
        }
        return result;
    }

    /** 校验商品正整数编号、存在性及操作者范围；范围校验不能由前端隐藏按钮替代。 */
    private ProductDO requireProduct(Long operatorId, Long productId) {
        if (productId == null || productId <= 0) throw badRequest("菜品编号无效");
        ProductDO product = products.selectById(productId);
        if (product == null) throw new BusinessException(CommonErrorCode.NOT_FOUND, "菜品不存在");
        scopes.checkWindowAccess(operatorId, product.getWindowId());
        return product;
    }

    /**
     * 服务层再次校验所有信任边界，保障内部调用也不能绕过Controller的Bean Validation。
     * 名称先去首尾空白并忽略大小写比较；编号去重防止同一记录被重复覆盖，金额限制与SQL精度一致。
     */
    private void validate(ProductSpecificationsSaveRequest request,
            Map<Long, ProductSpecificationGroupDO> oldGroups, Map<Long, ProductSpecificationOptionDO> oldOptions) {
        if (request == null || request.getGroups() == null || request.getGroups().size() > 10)
            throw badRequest("规格组必须为数组，最多10组");
        Set<String> groupNames = new HashSet<>();
        Set<Long> groupIds = new HashSet<>();
        Set<Long> optionIds = new HashSet<>();
        for (ProductSpecificationsSaveRequest.Group group : request.getGroups()) {
            if (group == null || !validName(group.getName()) || !groupNames.add(normalizedName(group.getName()))
                    || group.getRequired() == null || group.getSortOrder() == null || group.getSortOrder() < 0
                    || group.getOptions() == null || group.getOptions().isEmpty() || group.getOptions().size() > 20)
                throw badRequest("规格组名称须唯一、排序非负且每组包含1至20个选项");
            if (group.getGroupId() != null && (!oldGroups.containsKey(group.getGroupId())
                    || !groupIds.add(group.getGroupId()))) throw badRequest("规格组编号不属于该菜品或重复");
            Set<String> optionNames = new HashSet<>();
            boolean hasZeroPrice = false;
            for (ProductSpecificationsSaveRequest.Option option : group.getOptions()) {
                if (option == null || !validName(option.getName()) || !optionNames.add(normalizedName(option.getName()))
                        || option.getSortOrder() == null || option.getSortOrder() < 0)
                    throw badRequest("选项名称须同组唯一且排序非负");
                BigDecimal price = option.getAdditionalPrice();
                if (price == null || price.signum() < 0 || price.scale() > 2
                        || price.compareTo(new BigDecimal("99999999.99")) > 0)
                    throw badRequest("选项加价须为非负金额，最多8位整数和2位小数");
                hasZeroPrice |= price.signum() == 0;
                if (option.getOptionId() != null) {
                    ProductSpecificationOptionDO old = oldOptions.get(option.getOptionId());
                    if (old == null || !old.getGroupId().equals(group.getGroupId())
                            || !optionIds.add(option.getOptionId())) throw badRequest("选项编号不属于该组或重复");
                }
            }
            if (Boolean.TRUE.equals(group.getRequired()) && !hasZeroPrice)
                throw badRequest("必选规格组至少需要一个零加价选项");
        }
    }

    /** 名称必须实际有内容，原始长度和去空白后长度均不超过SQL列限制。 */
    private boolean validName(String name) { return name != null && !name.isBlank() && name.length() <= 50; }

    /** 统一名称去空白及大小写规则，防止管理端以大小写或空格创建重复启用名。 */
    private String normalizedName(String name) { return name.strip().toLowerCase(Locale.ROOT); }

    /** 配置不合法统一返回400；异常抛出后整个规格事务回滚。 */
    private BusinessException badRequest(String message) { return new BusinessException(CommonErrorCode.BAD_REQUEST, message); }
}

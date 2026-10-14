package com.hbue.ordering.product.cache;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hbue.ordering.product.mapper.DiningWindowMapper;
import com.hbue.ordering.product.mapper.ProductMapper;
import com.hbue.ordering.product.model.DiningWindowDO;
import com.hbue.ordering.product.model.ProductDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商品布隆过滤器启动初始化器。
 *
 * <p>服务启动后把数据库中的窗口与菜品 ID 全量写入对应布隆过滤器，
 * 使服务运行初期即可用过滤器排除一部分确定不存在的 ID。</p>
 *
 * <p>过滤器只用于查询前的候选筛选，不判断启停或售卖状态；这些业务状态仍由数据库查询确认。
 * 初始化整体兜底异常：底层未初始化时按"可能存在"放行，避免误拒绝真实数据；
 * 初始化失败只记录日志，不阻止商品服务启动。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
@Component
public class ProductBloomFilterInitializer implements ApplicationRunner {

    /** 日志对象。 */
    private static final Logger log =
            LoggerFactory.getLogger(ProductBloomFilterInitializer.class);

    /** 商品布隆过滤器管理器。 */
    private final ProductBloomFilterManager bloomFilterManager;

    /** 窗口数据 Mapper。 */
    private final DiningWindowMapper diningWindowMapper;

    /** 菜品数据 Mapper。 */
    private final ProductMapper productMapper;

    /**
     * 创建布隆过滤器启动初始化器。
     *
     * @param bloomFilterManager 商品布隆过滤器管理器
     * @param diningWindowMapper 窗口数据 Mapper
     * @param productMapper 菜品数据 Mapper
     */
    public ProductBloomFilterInitializer(
            ProductBloomFilterManager bloomFilterManager,
            DiningWindowMapper diningWindowMapper,
            ProductMapper productMapper
    ) {
        this.bloomFilterManager = bloomFilterManager;
        this.diningWindowMapper = diningWindowMapper;
        this.productMapper = productMapper;
    }

    /**
     * 启动时初始化布隆过滤器并写入存量数据 ID。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            bloomFilterManager.initFilters();

            List<DiningWindowDO> windows = diningWindowMapper.selectList(
                    new QueryWrapper<DiningWindowDO>()
            );
            for (DiningWindowDO window : windows) {
                bloomFilterManager.addWindow(window.getWindowId());
            }

            List<ProductDO> products = productMapper.selectList(
                    new QueryWrapper<ProductDO>()
            );
            for (ProductDO product : products) {
                bloomFilterManager.addProduct(product.getProductId());
            }
        } catch (Exception exception) {
            // 初始化失败不影响服务可用：未初始化的布隆过滤器会放行所有请求。
            log.error("初始化商品布隆过滤器失败，已跳过存量数据加载", exception);
        }
    }
}

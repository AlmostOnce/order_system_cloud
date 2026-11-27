package com.hbue.ordering.search.service;

import com.hbue.ordering.search.vo.ProductSearchPageVO;

import java.util.List;

/**
 * 菜品搜索业务边界，供 HTTP 控制器和消息消费者共同调用。
 */
public interface ProductSearchService {
    ProductSearchPageVO search(String keyword, Long restaurantId, Long windowId, Long categoryId, int page, int size);

    /**
     * 按输入前缀返回菜名联想候选。
     *
     * <p>只做「输入中」的轻量前缀查询，不返回菜品详情，也不参与搜索意图采集；
     * 结果可能被缓存或限频降级为空列表，调用方不能把它当作「不存在该菜品」的结论。</p>
     *
     * @param keyword  用户当前输入的原始文案，允许首尾空白，内部会 trim
     * @param limit    期望返回的候选数量，取值会被收敛到 1～20
     * @param userUuid 当前登录用户的公开 UUID，仅用于限频计数；为空时按匿名维度计数
     * @return 去重后的菜名列表；命中限频或 Redis 异常时返回空列表，ES 查询本身失败仍按服务统一错误处理
     */
    List<String> suggest(String keyword, int limit, String userUuid);

    void refreshWindow(Long windowId);

    void refreshProduct(Long windowId, Long productId);

    int rebuildAll();

    /**
     * 将商品服务中现有窗口的菜品同步到搜索索引。
     *
     * @return 已同步的窗口数量
     */
    int synchronizeExistingWindows();

    /**
     * 对比商品快照和搜索索引，修复不一致的菜品，返回修复数量。
     */
    int reconcile();
}

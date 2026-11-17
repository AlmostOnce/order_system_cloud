package com.hbue.ordering.recommendation.service;

import java.util.List;

/** 从商品服务读取可推荐目录并维护本地商品特征投影。 */
public interface RecommendationProductCatalogService {
    /** 返回商品服务确认有效的完整窗口编号列表；失败必须抛出，不能误当作空目录。 */
    List<Long> windowIds();

    /** 读取并原子替换指定窗口的完整有效商品快照；上游读取失败时不得撤下旧数据。 */
    void refreshWindow(Long windowId);

    /** 刷新一个商品；空结果表示源端已确认商品不可推荐，其他商品状态保持不变。 */
    void refreshProduct(Long windowId, Long productId);

    /** 仅在完整窗口清单成功读取后调用，撤下已不存在窗口的历史候选。 */
    void markWindowsMissing(List<Long> activeWindowIds);
}

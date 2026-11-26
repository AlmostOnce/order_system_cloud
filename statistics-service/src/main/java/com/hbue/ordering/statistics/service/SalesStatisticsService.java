package com.hbue.ordering.statistics.service;

import com.hbue.ordering.statistics.messaging.SalesPaidEvent;
import com.hbue.ordering.statistics.vo.SalesRankingVO;
import com.hbue.ordering.statistics.vo.WindowMonthlySalesVO;
import com.hbue.ordering.statistics.vo.WindowProductMonthlySalesVO;

/**
 * 统计服务的业务编排入口。
 *
 * <p>只做编排与兼容处理，不直接访问数据库：HTTP 查询转交投影服务或榜单缓存服务，
 * RocketMQ 消费负责在"消息快照"与"订单内部接口回读"之间做版本兼容。</p>
 *
 * <p>该方法集同时被控制器与消息消费者使用，因此失败语义按调用方区分：查询类失败抛业务异常，
 * 消费类失败必须向上抛出让 Broker 重投。</p>
 */
public interface SalesStatisticsService {

    /**
     * 查询累计菜品销量榜。
     *
     * @param limit 榜单数量，范围校验由缓存服务完成
     * @return 按销量降序的榜单
     */
    SalesRankingVO listRanking(int limit);

    /**
     * 消费已支付订单事件并更新统计投影与榜单缓存。
     *
     * @param event RocketMQ 收到的 V1 或 V2 事件
     */
    void consume(SalesPaidEvent event);

    /**
     * 批量读取所有窗口当前自然月总份数，供首页一次性展示。
     *
     * @return 窗口月销量视图
     */
    WindowMonthlySalesVO listWindowMonthlySales();

    /**
     * 读取单个窗口的总月售及其菜品月销量，供点餐页展示。
     *
     * @param windowId 窗口编号
     * @return 窗口与菜品月销量视图
     */
    WindowProductMonthlySalesVO getWindowMonthlySales(Long windowId);
}

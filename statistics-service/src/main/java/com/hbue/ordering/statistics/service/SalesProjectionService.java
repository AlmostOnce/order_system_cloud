package com.hbue.ordering.statistics.service;

import com.hbue.ordering.order.api.vo.OrderPaidSalesSnapshotVO;
import com.hbue.ordering.statistics.messaging.SalesPaidEvent;
import com.hbue.ordering.statistics.vo.WindowMonthlySalesVO;
import com.hbue.ordering.statistics.vo.WindowProductMonthlySalesVO;

/**
 * 销量投影的读写边界。
 *
 * <p>写侧负责在 statistics_db 内原子保存事件事实、订单菜品明细与累计/月度汇总；读侧负责把
 * 已落库的月度汇总装配成学生端视图。把读写都收在同一接口内，是为了让 HTTP 查询和 MQ 消费
 * 共享同一份"哪个库、哪张表才是权威"的约定，调用方不再直接接触 Mapper。</p>
 *
 * <p>跨实例幂等由数据库唯一键负责：重复消息必须与既有快照一致，否则抛出异常交由 MQ 重试和
 * 死信排查，不能把不同业务载荷误当成正常重复而静默吞掉。</p>
 */
public interface SalesProjectionService {

    /**
     * 判断稳定事件键是否已通过历史迁移或先前消费写入。
     *
     * <p>仅用于旧版 V1 消息的快速短路：命中即可跳过订单快照回读，未命中才继续走正常流程。</p>
     *
     * @param eventId 由订单号派生的稳定事件键
     * @return 已存在返回 true
     */
    boolean alreadyRecorded(String eventId);

    /**
     * 原子持久化已支付快照，并返回提交后的 MySQL 投影版本。
     *
     * @param event    RocketMQ 上收到的事件版本
     * @param snapshot 已由 V2 消息携带或由订单内部接口补齐的购买事实
     * @return 当前权威投影版本，重复投递不会递增该值
     */
    long record(SalesPaidEvent event, OrderPaidSalesSnapshotVO snapshot);

    /**
     * 查询所有窗口当前自然月的总份数。
     *
     * <p>供首页一次性批量读取，避免客户端对每个窗口各发一次请求。</p>
     *
     * @return 窗口编号到月销量的映射视图
     */
    WindowMonthlySalesVO listWindowMonthlySales();

    /**
     * 查询单个窗口的自然月总份数及其菜品月销量。
     *
     * <p>月份以支付时刻归属，由部署时区固定为 Asia/Shanghai 计算，避免主机默认时区变化导致跨月错位。</p>
     *
     * @param windowId 窗口编号，必须为正数
     * @return 窗口与菜品月销量视图
     */
    WindowProductMonthlySalesVO getWindowMonthlySales(Long windowId);
}

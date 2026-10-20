package com.hbue.ordering.stock.task;

import com.hbue.ordering.stock.mapper.StockFlowMapper;
import com.hbue.ordering.stock.mapper.StockMapper;
import com.hbue.ordering.stock.model.StockDO;
import com.hbue.ordering.stock.support.StockRedisScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 库存定时对账任务。
 *
 * <p>库存存在两处派生风险，需要定时兜底校正：</p>
 * <ol>
 *     <li>Redis 快照是加速用的派生数据：预扣补偿失败、进程异常退出、快照过期重建
 *         都可能让快照与数据库可售数量不一致。对账以数据库为准，把不一致的快照改回正确值。</li>
 *     <li>库存主表与流水理论上应恒等（当前可售数量 = 总可售上限 + 全部流水变动之和），
 *         若不等说明存在绕过流水的写入或程序缺陷，此时只告警不自动改写，
 *         因为无法从数据判断哪一侧才是正确值，自动修正会掩盖真实缺陷。</li>
 * </ol>
 *
 * <p>对账只修正派生数据，不会修改可售数量本身，因此不会引入超卖风险。</p>
 *
 * @author order-system
 * @date 2026-10-19
 */
@Component
@ConditionalOnProperty(
        prefix = "stock.reconcile",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class StockReconcileTask {

    /** 日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(StockReconcileTask.class);

    /** 库存主表数据访问接口。 */
    private final StockMapper stockMapper;

    /** 库存流水数据访问接口。 */
    private final StockFlowMapper stockFlowMapper;

    /** 库存快照 Redis 原子操作组件。 */
    private final StockRedisScript stockRedisScript;

    /**
     * 创建库存定时对账任务。
     *
     * @param stockMapper 库存主表数据访问接口
     * @param stockFlowMapper 库存流水数据访问接口
     * @param stockRedisScript 库存快照 Redis 原子操作组件
     */
    public StockReconcileTask(
            StockMapper stockMapper,
            StockFlowMapper stockFlowMapper,
            StockRedisScript stockRedisScript
    ) {
        this.stockMapper = stockMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.stockRedisScript = stockRedisScript;
    }

    /**
     * 执行一次库存对账。
     *
     * <p>开始后延迟一小段时间再执行，避免服务刚启动、依赖尚未就绪时触发；
     * 之后按固定间隔反复执行。任务内部逐条捕获异常，单条数据异常不会中断整轮对账。</p>
     */
    @Scheduled(
            initialDelayString = "${stock.reconcile.initial-delay-ms:60000}",
            fixedDelayString = "${stock.reconcile.fixed-delay-ms:600000}"
    )
    public void reconcile() {
        List<StockDO> stocks = stockMapper.selectAllForReconcile();
        if (stocks.isEmpty()) {
            log.info("库存对账跳过：没有需要比对的库存记录");
            return;
        }

        // 统计本轮结果，便于通过日志快速判断是否存在持续漂移。
        int snapshotFixed = 0;
        int flowMismatch = 0;
        // Redis 整体不可用时不再逐条重试，本轮只做流水自洽校验。
        boolean redisAvailable = true;

        for (StockDO stock : stocks) {
            if (redisAvailable) {
                try {
                    if (reconcileSnapshot(stock)) {
                        snapshotFixed++;
                    }
                } catch (RuntimeException exception) {
                    // Redis 整体不可用时不再逐条重试，本轮只继续做流水自洽校验。
                    log.warn(
                            "读取库存快照失败，本轮不再比对快照：档口 {} 菜品 {}",
                            stock.getWindowId(),
                            stock.getProductId(),
                            exception
                    );
                    redisAvailable = false;
                }
            }
            if (!isFlowConsistent(stock)) {
                flowMismatch++;
            }
        }

        log.info(
                "库存对账完成：检查 {} 条，修正快照 {} 条，流水不一致 {} 条",
                stocks.size(),
                snapshotFixed,
                flowMismatch
        );
    }

    /**
     * 比对并修正单条库存的 Redis 快照。
     *
     * <p>读取快照时的 Redis 连接异常不在本方法内处理，直接向调用方抛出，
     * 由调用方判断「Redis 本轮不可用」并停止后续快照比对。</p>
     *
     * @param stock 库存记录
     * @return 是否修正了快照
     */
    private boolean reconcileSnapshot(StockDO stock) {
        Long windowId = stock.getWindowId();
        Long productId = stock.getProductId();
        Integer snapshot = stockRedisScript.readSnapshot(windowId, productId);
        if (snapshot == null) {
            // 快照不存在属于正常情况（未下单的库存不会写入快照，或快照已过期），
            // 无需预热：真正的下单请求会按数据库值重建，对账不应替它们做这件事。
            return false;
        }
        if (snapshot.equals(stock.getAvailableQty())) {
            return false;
        }

        // 发现漂移：数据库是唯一事实来源，直接按数据库值改写快照。
        log.warn(
                "库存快照与数据库不一致，按数据库值修正：档口 {} 菜品 {} 快照 {} 数据库 {}",
                windowId,
                productId,
                snapshot,
                stock.getAvailableQty()
        );
        try {
            stockRedisScript.resetSnapshot(windowId, productId, stock.getAvailableQty());
        } catch (RuntimeException exception) {
            // 修正失败不影响后续记录：快照会在过期后被数据库值重建。
            log.warn("修正库存快照失败，等待下次对账或快照过期重建：{}:{}", windowId, productId, exception);
            return false;
        }
        return true;
    }

    /**
     * 校验单条库存的主表与流水是否自洽。
     *
     * <p>判据为「总可售上限 + 流水变动累计 = 当前可售数量」：
     * 库存创建时可售数量等于总上限，之后的一切变化都应留下流水。</p>
     *
     * @param stock 库存记录
     * @return 是否自洽
     */
    private boolean isFlowConsistent(StockDO stock) {
        int flowSum = stockFlowMapper.sumChangeQty(stock.getStockId());
        int expectedAvailable = stock.getTotalQty() + flowSum;
        if (expectedAvailable == stock.getAvailableQty()) {
            return true;
        }
        // 只告警不自动改写：两侧数据来源不同，自动修正可能掩盖真实缺陷。
        log.warn(
                "库存主表与流水不一致，需人工核对：库存 {} 总上限 {} 流水累计 {} 期望可售 {} 实际可售 {}",
                stock.getStockId(),
                stock.getTotalQty(),
                flowSum,
                expectedAvailable,
                stock.getAvailableQty()
        );
        return false;
    }
}

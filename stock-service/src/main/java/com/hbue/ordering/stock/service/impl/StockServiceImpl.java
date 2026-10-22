package com.hbue.ordering.stock.service.impl;

import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.stock.api.dto.StockDeductRequest;
import com.hbue.ordering.stock.api.dto.StockItemRequest;
import com.hbue.ordering.stock.api.dto.StockRestoreRequest;
import com.hbue.ordering.stock.api.error.StockErrorCode;
import com.hbue.ordering.stock.api.vo.StockVO;
import com.hbue.ordering.stock.mapper.StockFlowMapper;
import com.hbue.ordering.stock.mapper.StockMapper;
import com.hbue.ordering.stock.model.StockDO;
import com.hbue.ordering.stock.model.StockFlowDO;
import com.hbue.ordering.stock.service.StockService;
import com.hbue.ordering.stock.support.StockFlowTypes;
import com.hbue.ordering.stock.support.StockRedisScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 库存业务实现。
 *
 * <p>三条防线共同保证「不超卖」与「幂等」：</p>
 * <ol>
 *     <li>Redis 原子预扣：把必然失败的请求挡在数据库之外（可降级，不作为事实来源）；</li>
 *     <li>数据库行锁 + 乐观锁条件更新：真正的可售数量判据，并发下由数据库串行化；</li>
 *     <li>{@code stock_flow} 唯一键：同一订单同一菜品的同类变动只能记账一次。</li>
 * </ol>
 *
 * <p>处理顺序固定为「先加行锁读库存 → 查流水判重 → 原子扣减 → 写流水」。
 * 行锁使同一库存行的处理串行化，后到的事务能读到前一个事务已提交的流水，
 * 因此重复请求会走幂等分支直接返回，而不会重复扣减或重复回补。</p>
 *
 * <p>Redis 只做加速：访问异常或快照缺失时退化为纯数据库扣减，
 * 数据库事务失败时补偿已预扣的快照，快照与数据库的漂移由定时对账修正。</p>
 *
 * @author order-system
 * @date 2026-10-19
 */
@Service
public class StockServiceImpl implements StockService {

    @Override
    public List<StockVO> getWindowStocks(Long windowId) {
        return stockMapper.selectByWindow(windowId).stream()
                .map(stock -> StockVO.builder()
                        .stockId(stock.getStockId()).windowId(stock.getWindowId())
                        .productId(stock.getProductId()).totalQty(stock.getTotalQty())
                        .availableQty(stock.getAvailableQty()).build())
                .toList();
    }

    /** 日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(StockServiceImpl.class);

    /** Redis 预扣成功，失败时需要补偿。 */
    private static final int SNAPSHOT_DEDUCTED = 1;

    /** Redis 不可用或快照缺失，本次跳过预扣，直接走数据库。 */
    private static final int SNAPSHOT_SKIPPED = 0;

    /** 库存主表数据访问接口。 */
    private final StockMapper stockMapper;

    /** 库存流水数据访问接口。 */
    private final StockFlowMapper stockFlowMapper;

    /** 库存快照 Redis 原子操作组件。 */
    private final StockRedisScript stockRedisScript;

    /**
     * 创建库存业务实现。
     *
     * @param stockMapper 库存主表数据访问接口
     * @param stockFlowMapper 库存流水数据访问接口
     * @param stockRedisScript 库存快照 Redis 原子操作组件
     */
    public StockServiceImpl(
            StockMapper stockMapper,
            StockFlowMapper stockFlowMapper,
            StockRedisScript stockRedisScript
    ) {
        this.stockMapper = stockMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.stockRedisScript = stockRedisScript;
    }

    /**
     * 扣减库存。
     *
     * <p>整个请求（可含多行菜品）在同一数据库事务内完成：任意一行失败即整体回滚，
     * 因此不会出现「部分扣减被当作成功」的情况。已先行预扣的 Redis 快照在异常时统一补偿。</p>
     *
     * @param request 扣减请求
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deduct(StockDeductRequest request) {
        Long windowId = request.getWindowId();
        // 记录本请求中已成功预扣 Redis 快照的明细，事务失败时逐条补偿回去。
        List<StockItemRequest> preDeductedItems = new ArrayList<>();

        try {
            for (StockItemRequest item : request.getItems()) {
                deductSingleItem(request, item, preDeductedItems);
            }
        } catch (RuntimeException exception) {
            // 数据库事务即将回滚，同步撤销 Redis 侧已先行扣减的快照，避免快照低于真实可售数量。
            compensateSnapshots(windowId, preDeductedItems);
            throw exception;
        }
    }

    /**
     * 回补库存。
     *
     * <p>回补不做 Redis 预扣（方向相反），只在数据库侧加回，
     * 并在成功后把快照重置为数据库值，使快照尽快收敛。</p>
     *
     * @param request 回补请求
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restore(StockRestoreRequest request) {
        for (StockItemRequest item : request.getItems()) {
            restoreSingleItem(request, item);
        }
    }

    /**
     * 查询指定档口菜品的库存快照（以数据库为准）。
     *
     * @param windowId 档口 ID
     * @param productId 菜品 ID
     * @return 库存视图；无记录时返回 null
     */
    @Override
    public StockVO getStock(Long windowId, Long productId) {
        StockDO stock = stockMapper.selectByWindowProduct(windowId, productId);
        if (stock == null) {
            return null;
        }
        return StockVO.builder()
                .stockId(stock.getStockId())
                .windowId(stock.getWindowId())
                .productId(stock.getProductId())
                .totalQty(stock.getTotalQty())
                .availableQty(stock.getAvailableQty())
                .build();
    }

    /**
     * 扣减单行菜品库存。
     *
     * <p>步骤：加行锁读库存 → 幂等预检 → Redis 原子预扣 → 乐观锁条件扣减 → 写扣减流水。</p>
     *
     * @param request 扣减请求
     * @param item 单行菜品明细
     * @param preDeductedItems 已预扣 Redis 快照的明细集合，用于失败补偿
     */
    private void deductSingleItem(
            StockDeductRequest request,
            StockItemRequest item,
            List<StockItemRequest> preDeductedItems
    ) {
        Long windowId = request.getWindowId();
        Long productId = item.getProductId();
        Integer quantity = item.getQuantity();

        // 先取行锁：把同一库存行的「判重 + 扣减 + 记账」串行化，并读到最新已提交数据。
        StockDO stock = stockMapper.selectByWindowProductForUpdate(windowId, productId);
        if (stock == null) {
            throw new BusinessException(
                    StockErrorCode.STOCK_NOT_FOUND,
                    "档口 " + windowId + " 的菜品 " + productId + " 没有库存记录"
            );
        }

        // 幂等预检：该订单对该菜品已扣减过则直接返回，不再改动任何数量。
        int deductedCount = stockFlowMapper.countByBiz(
                StockFlowTypes.ORDER_DEDUCT,
                request.getBizRef(),
                stock.getStockId()
        );
        if (deductedCount > 0) {
            log.info(
                    "订单 {} 的菜品 {} 已扣减过库存，按幂等处理直接返回",
                    request.getBizRef(),
                    productId
            );
            return;
        }

        // Redis 原子预扣：把库存不足的请求快速挡在数据库之外。
        int snapshotStatus = preDeductSnapshot(stock, quantity);
        if (snapshotStatus == StockRedisScript.SNAPSHOT_INSUFFICIENT) {
            throw notEnough(windowId, productId, stock.getAvailableQty(), quantity);
        }
        if (snapshotStatus == SNAPSHOT_DEDUCTED) {
            // 仅登记真正被扣减过快照的明细，降级跳过预扣的明细不需要补偿。
            preDeductedItems.add(item);
        }

        // 数据库乐观锁条件扣减：真正的不超卖判据。
        int affected = stockMapper.deductAvailable(
                stock.getStockId(),
                stock.getVersion(),
                quantity
        );
        if (affected != 1) {
            // 快照与数据库漂移或并发冲突，此时数据库才是权威判据。
            throw notEnough(windowId, productId, stock.getAvailableQty(), quantity);
        }

        // 记账：唯一键 (biz_type, biz_ref, stock_id) 保证同一单据只记一次。
        int afterQty = stock.getAvailableQty() - quantity;
        stockFlowMapper.insert(buildFlow(
                StockFlowTypes.ORDER_DEDUCT,
                request.getBizRef(),
                stock,
                -quantity,
                stock.getAvailableQty(),
                afterQty,
                request.getOperatorId(),
                request.getRemark()
        ));
        log.info(
                "订单 {} 扣减档口 {} 菜品 {} 库存 {} 件，剩余 {}",
                request.getBizRef(),
                windowId,
                productId,
                quantity,
                afterQty
        );
    }

    /**
     * 回补单行菜品库存。
     *
     * <p>步骤：加行锁读库存 → 查回补流水判重 → 校验确有扣减记录 → 加回数量 → 写回补流水 → 重置快照。</p>
     *
     * @param request 回补请求
     * @param item 单行菜品明细
     */
    private void restoreSingleItem(StockRestoreRequest request, StockItemRequest item) {
        Long windowId = request.getWindowId();
        Long productId = item.getProductId();
        Integer quantity = item.getQuantity();

        StockDO stock = stockMapper.selectByWindowProductForUpdate(windowId, productId);
        if (stock == null) {
            throw new BusinessException(
                    StockErrorCode.STOCK_NOT_FOUND,
                    "档口 " + windowId + " 的菜品 " + productId + " 没有库存记录"
            );
        }

        // 幂等预检：同一单据同一菜品已回补过则直接返回，绝不重复加库存。
        int restoredCount = stockFlowMapper.countByBiz(
                StockFlowTypes.ORDER_RESTORE,
                request.getBizRef(),
                stock.getStockId()
        );
        if (restoredCount > 0) {
            log.info(
                    "订单 {} 的菜品 {} 已回补过库存，按幂等处理直接返回",
                    request.getBizRef(),
                    productId
            );
            return;
        }

        // 没有对应的扣减流水说明该单据从未扣减过库存，回补会把库存凭空加多，
        // 因此按无操作处理并告警，交由人工核对。
        int deductedCount = stockFlowMapper.countByBiz(
                StockFlowTypes.ORDER_DEDUCT,
                request.getBizRef(),
                stock.getStockId()
        );
        if (deductedCount == 0) {
            log.warn(
                    "订单 {} 的菜品 {} 没有扣减流水，跳过回补以避免库存凭空增加",
                    request.getBizRef(),
                    productId
            );
            return;
        }

        int affected = stockMapper.restoreQuantity(stock.getStockId(), quantity);
        if (affected != 1) {
            throw new BusinessException(
                    StockErrorCode.STOCK_CONFLICT,
                    "档口 " + windowId + " 的菜品 " + productId + " 回补库存失败"
            );
        }

        int afterQty = stock.getAvailableQty() + quantity;
        stockFlowMapper.insert(buildFlow(
                StockFlowTypes.ORDER_RESTORE,
                request.getBizRef(),
                stock,
                quantity,
                stock.getAvailableQty(),
                afterQty,
                request.getOperatorId(),
                request.getRemark()
        ));

        // 回补已完成，把 Redis 快照对齐到数据库值。快照只影响 Redis 侧的快速过滤，
        // 每次扣减仍会由数据库条件更新兜底，因此对齐不会造成超卖。
        resetSnapshotQuietly(windowId, productId, afterQty);
        log.info(
                "订单 {} 回补档口 {} 菜品 {} 库存 {} 件，剩余 {}",
                request.getBizRef(),
                windowId,
                productId,
                quantity,
                afterQty
        );
    }

    /**
     * Redis 原子预扣，并在快照缺失时用数据库值预热后重试一次。
     *
     * <p>Redis 访问异常时降级返回「跳过预扣」，由数据库独立完成扣减判断，
     * 保证 Redis 故障不会阻断下单，也不会让 Redis 成为事实来源。</p>
     *
     * @param stock 当前库存记录
     * @param quantity 需要扣减的数量
     * @return 预扣成功返回 {@link #SNAPSHOT_DEDUCTED}；库存不足返回
     *         {@link StockRedisScript#SNAPSHOT_INSUFFICIENT}；降级跳过返回 {@link #SNAPSHOT_SKIPPED}
     */
    private int preDeductSnapshot(StockDO stock, Integer quantity) {
        Long windowId = stock.getWindowId();
        Long productId = stock.getProductId();
        try {
            int result = stockRedisScript.preDeduct(windowId, productId, quantity);
            if (result == StockRedisScript.SNAPSHOT_MISSING) {
                // 快照不存在或已过期，用数据库可售数量重建后再预扣一次。
                stockRedisScript.resetSnapshot(
                        windowId,
                        productId,
                        stock.getAvailableQty()
                );
                result = stockRedisScript.preDeduct(windowId, productId, quantity);
            }
            if (result == StockRedisScript.SNAPSHOT_INSUFFICIENT) {
                return StockRedisScript.SNAPSHOT_INSUFFICIENT;
            }
            if (result == StockRedisScript.SNAPSHOT_MISSING) {
                // 预热后仍读不到快照说明 Redis 状态异常，降级为纯数据库扣减。
                log.warn("库存快照重建后仍不可读，本次降级为纯数据库扣减：{}:{}", windowId, productId);
                return SNAPSHOT_SKIPPED;
            }
            return SNAPSHOT_DEDUCTED;
        } catch (RuntimeException exception) {
            // Redis 故障不能阻断下单：只记录告警并降级，数据库仍是唯一事实来源。
            log.warn("Redis 预扣库存失败，本次降级为纯数据库扣减：{}:{}", windowId, productId, exception);
            return SNAPSHOT_SKIPPED;
        }
    }

    /**
     * 补偿本请求中已预扣的 Redis 快照。
     *
     * @param windowId 档口 ID
     * @param preDeductedItems 已预扣的菜品明细
     */
    private void compensateSnapshots(Long windowId, List<StockItemRequest> preDeductedItems) {
        for (StockItemRequest item : preDeductedItems) {
            stockRedisScript.compensate(windowId, item.getProductId(), item.getQuantity());
        }
    }

    /**
     * 重置快照并吞掉 Redis 异常。
     *
     * <p>快照是派生数据，重置失败只影响后续的快速过滤精度，定时对账会把漂移修正，
     * 因此不能因此让已经成功的回补事务失败。</p>
     *
     * @param windowId 档口 ID
     * @param productId 菜品 ID
     * @param availableQty 数据库中的可售数量
     */
    private void resetSnapshotQuietly(Long windowId, Long productId, Integer availableQty) {
        try {
            stockRedisScript.resetSnapshot(windowId, productId, availableQty);
        } catch (RuntimeException exception) {
            log.warn("重置库存快照失败，等待定时对账修正：{}:{}", windowId, productId, exception);
        }
    }

    /**
     * 组装库存流水记录。
     *
     * @param bizType 业务类型
     * @param bizRef 业务单据号
     * @param stock 变更前的库存记录
     * @param changeQty 变动数量（扣减为负、回补为正）
     * @param beforeQty 变动前可售数量
     * @param afterQty 变动后可售数量
     * @param operatorId 操作者 user_id
     * @param remark 备注
     * @return 库存流水持久化对象
     */
    private StockFlowDO buildFlow(
            String bizType,
            String bizRef,
            StockDO stock,
            Integer changeQty,
            Integer beforeQty,
            Integer afterQty,
            Long operatorId,
            String remark
    ) {
        StockFlowDO flow = new StockFlowDO();
        flow.setBizType(bizType);
        flow.setBizRef(bizRef);
        flow.setStockId(stock.getStockId());
        flow.setWindowId(stock.getWindowId());
        flow.setProductId(stock.getProductId());
        flow.setChangeQty(changeQty);
        flow.setBeforeQty(beforeQty);
        flow.setAfterQty(afterQty);
        flow.setOperatorId(operatorId);
        flow.setRemark(remark);
        flow.setCreatedAt(LocalDateTime.now());
        return flow;
    }

    /**
     * 构造统一的库存不足异常。
     *
     * @param windowId 档口 ID
     * @param productId 菜品 ID
     * @param availableQty 当前可售数量
     * @param quantity 请求扣减数量
     * @return 库存不足业务异常
     */
    private BusinessException notEnough(
            Long windowId,
            Long productId,
            Integer availableQty,
            Integer quantity
    ) {
        return new BusinessException(
                StockErrorCode.STOCK_NOT_ENOUGH,
                "档口 " + windowId + " 的菜品 " + productId + " 库存不足：可售 "
                        + availableQty + "，需要 " + quantity
        );
    }
}

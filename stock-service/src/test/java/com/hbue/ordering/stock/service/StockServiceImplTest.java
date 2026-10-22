package com.hbue.ordering.stock.service;

import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.stock.api.dto.StockDeductRequest;
import com.hbue.ordering.stock.api.dto.StockItemRequest;
import com.hbue.ordering.stock.api.dto.StockRestoreRequest;
import com.hbue.ordering.stock.api.vo.StockVO;
import com.hbue.ordering.stock.mapper.StockFlowMapper;
import com.hbue.ordering.stock.mapper.StockMapper;
import com.hbue.ordering.stock.model.StockDO;
import com.hbue.ordering.stock.model.StockFlowDO;
import com.hbue.ordering.stock.service.impl.StockServiceImpl;
import com.hbue.ordering.stock.support.StockFlowTypes;
import com.hbue.ordering.stock.support.StockRedisScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 库存扣减与回补的业务规则单元测试。
 *
 * <p>覆盖三条关键防线：库存不足时不落库、重复请求不重复扣减/回补、
 * Redis 不可用时降级为纯数据库扣减。测试直接驱动业务实现，不启动 Spring 容器。</p>
 *
 * @author order-system
 * @date 2026-10-19
 */
@ExtendWith(MockitoExtension.class)
class StockServiceImplTest {

    /** 档口 ID。 */
    private static final Long WINDOW_ID = 1001L;

    /** 菜品 ID。 */
    private static final Long PRODUCT_ID = 2001L;

    /** 库存记录 ID。 */
    private static final Long STOCK_ID = 3001L;

    /** 订单号。 */
    private static final String ORDER_NO = "ORD20261019001";

    /** 库存主表数据访问接口。 */
    @Mock
    private StockMapper stockMapper;

    /** 库存流水数据访问接口。 */
    @Mock
    private StockFlowMapper stockFlowMapper;

    /** 库存快照 Redis 原子操作组件。 */
    @Mock
    private StockRedisScript stockRedisScript;

    /** 被测业务实现。 */
    private StockServiceImpl stockService;

    /**
     * 为每个用例构造被测对象。
     */
    @BeforeEach
    void setUp() {
        stockService = new StockServiceImpl(stockMapper, stockFlowMapper, stockRedisScript);
    }

    /**
     * 首次扣减：库存充足时应扣减数据库数量并写入扣减流水。
     */
    @Test
    @DisplayName("首次扣减成功：数据库减库存并记录扣减流水")
    void deductFirstTime() {
        // 库存记录：可售 10 件，版本 0。
        StockDO stock = buildStock(10, 0);
        when(stockMapper.selectByWindowProductForUpdate(WINDOW_ID, PRODUCT_ID))
                .thenReturn(stock);
        // 该订单尚未扣减过。
        when(stockFlowMapper.countByBiz(anyString(), anyString(), anyLong()))
                .thenReturn(0);
        // Redis 预扣成功，剩余 8 件。
        when(stockRedisScript.preDeduct(WINDOW_ID, PRODUCT_ID, 2)).thenReturn(8);
        // 数据库条件扣减成功。
        when(stockMapper.deductAvailable(STOCK_ID, 0, 2)).thenReturn(1);

        stockService.deduct(buildDeductRequest(2));

        // 校验扣减与流水的前后数量。
        verify(stockMapper).deductAvailable(STOCK_ID, 0, 2);
        ArgumentCaptor<StockFlowDO> flowCaptor = ArgumentCaptor.forClass(StockFlowDO.class);
        verify(stockFlowMapper).insert(flowCaptor.capture());
        StockFlowDO flow = flowCaptor.getValue();
        assertEquals(StockFlowTypes.ORDER_DEDUCT, flow.getBizType());
        assertEquals(ORDER_NO, flow.getBizRef());
        assertEquals(-2, flow.getChangeQty());
        assertEquals(10, flow.getBeforeQty());
        assertEquals(8, flow.getAfterQty());
    }

    /**
     * 重复扣减：同一订单同一菜品已记过扣减流水时应直接返回，不再改动任何数据。
     */
    @Test
    @DisplayName("重复扣减幂等：已有扣减流水时直接返回，不重复扣减")
    void deductIdempotent() {
        when(stockMapper.selectByWindowProductForUpdate(WINDOW_ID, PRODUCT_ID))
                .thenReturn(buildStock(10, 0));
        // 已存在扣减流水。
        when(stockFlowMapper.countByBiz(anyString(), anyString(), anyLong()))
                .thenReturn(1);

        stockService.deduct(buildDeductRequest(2));

        verify(stockRedisScript, never()).preDeduct(anyLong(), anyLong(), anyInt());
        verify(stockMapper, never()).deductAvailable(anyLong(), anyInt(), anyInt());
        verify(stockFlowMapper, never()).insert(any(StockFlowDO.class));
    }

    /**
     * 库存不足：Redis 预扣直接拒绝时应返回业务失败，且不触碰数据库。
     */
    @Test
    @DisplayName("库存不足：预扣失败即拒绝，数据库不被修改")
    void deductNotEnough() {
        when(stockMapper.selectByWindowProductForUpdate(WINDOW_ID, PRODUCT_ID))
                .thenReturn(buildStock(1, 0));
        when(stockFlowMapper.countByBiz(anyString(), anyString(), anyLong()))
                .thenReturn(0);
        when(stockRedisScript.preDeduct(WINDOW_ID, PRODUCT_ID, 2))
                .thenReturn(StockRedisScript.SNAPSHOT_INSUFFICIENT);

        assertThrows(BusinessException.class, () -> stockService.deduct(buildDeductRequest(2)));

        verify(stockMapper, never()).deductAvailable(anyLong(), anyInt(), anyInt());
        verify(stockFlowMapper, never()).insert(any(StockFlowDO.class));
    }

    /**
     * Redis 故障降级：预扣抛异常时应继续走数据库扣减，不能让 Redis 阻断下单。
     */
    @Test
    @DisplayName("Redis 故障降级：仍由数据库完成扣减")
    void deductDegradeWhenRedisDown() {
        when(stockMapper.selectByWindowProductForUpdate(WINDOW_ID, PRODUCT_ID))
                .thenReturn(buildStock(10, 0));
        when(stockFlowMapper.countByBiz(anyString(), anyString(), anyLong()))
                .thenReturn(0);
        when(stockRedisScript.preDeduct(WINDOW_ID, PRODUCT_ID, 2))
                .thenThrow(new IllegalStateException("Redis 连接失败"));
        when(stockMapper.deductAvailable(STOCK_ID, 0, 2)).thenReturn(1);

        stockService.deduct(buildDeductRequest(2));

        verify(stockMapper).deductAvailable(STOCK_ID, 0, 2);
        verify(stockFlowMapper).insert(any(StockFlowDO.class));
    }

    /**
     * 快照缺失：应先用数据库值重建快照再预扣，成功后照常扣减。
     */
    @Test
    @DisplayName("快照缺失：回源数据库重建后继续扣减")
    void deductRebuildSnapshotWhenMissing() {
        when(stockMapper.selectByWindowProductForUpdate(WINDOW_ID, PRODUCT_ID))
                .thenReturn(buildStock(10, 0));
        when(stockFlowMapper.countByBiz(anyString(), anyString(), anyLong()))
                .thenReturn(0);
        // 首次读不到快照，重建后可正常预扣。
        when(stockRedisScript.preDeduct(WINDOW_ID, PRODUCT_ID, 2))
                .thenReturn(StockRedisScript.SNAPSHOT_MISSING)
                .thenReturn(8);
        when(stockMapper.deductAvailable(STOCK_ID, 0, 2)).thenReturn(1);

        stockService.deduct(buildDeductRequest(2));

        verify(stockRedisScript).resetSnapshot(WINDOW_ID, PRODUCT_ID, 10);
        verify(stockRedisScript, times(2)).preDeduct(WINDOW_ID, PRODUCT_ID, 2);
        verify(stockMapper).deductAvailable(STOCK_ID, 0, 2);
    }

    /**
     * 数据库扣减失败：应补偿已预扣的 Redis 快照并抛出业务失败。
     */
    @Test
    @DisplayName("数据库扣减失败：补偿 Redis 快照并回滚")
    void deductCompensateWhenDatabaseFails() {
        when(stockMapper.selectByWindowProductForUpdate(WINDOW_ID, PRODUCT_ID))
                .thenReturn(buildStock(10, 0));
        when(stockFlowMapper.countByBiz(anyString(), anyString(), anyLong()))
                .thenReturn(0);
        when(stockRedisScript.preDeduct(WINDOW_ID, PRODUCT_ID, 2)).thenReturn(8);
        // 数据库判据认为库存不足（快照与数据库漂移）。
        when(stockMapper.deductAvailable(STOCK_ID, 0, 2)).thenReturn(0);

        assertThrows(BusinessException.class, () -> stockService.deduct(buildDeductRequest(2)));

        verify(stockRedisScript).compensate(WINDOW_ID, PRODUCT_ID, 2);
        verify(stockFlowMapper, never()).insert(any(StockFlowDO.class));
    }

    /**
     * 回补幂等：已回补过时直接返回，不重复加库存。
     */
    @Test
    @DisplayName("回补幂等：已有回补流水时直接返回")
    void restoreIdempotent() {
        when(stockMapper.selectByWindowProductForUpdate(WINDOW_ID, PRODUCT_ID))
                .thenReturn(buildStock(8, 1));
        when(stockFlowMapper.countByBiz(anyString(), anyString(), anyLong()))
                .thenReturn(1);

        stockService.restore(buildRestoreRequest(2));

        verify(stockMapper, never()).restoreQuantity(anyLong(), anyInt());
        verify(stockFlowMapper, never()).insert(any(StockFlowDO.class));
    }

    /**
     * 回补前置校验：没有扣减流水时不允许回补，避免库存凭空增加。
     */
    @Test
    @DisplayName("无扣减记录不回补：避免库存凭空增加")
    void restoreSkipWithoutDeductFlow() {
        when(stockMapper.selectByWindowProductForUpdate(WINDOW_ID, PRODUCT_ID))
                .thenReturn(buildStock(10, 0));
        // 回补流水不存在，扣减流水也不存在。
        when(stockFlowMapper.countByBiz(anyString(), anyString(), anyLong()))
                .thenReturn(0);

        stockService.restore(buildRestoreRequest(2));

        verify(stockMapper, never()).restoreQuantity(anyLong(), anyInt());
        verify(stockFlowMapper, never()).insert(any(StockFlowDO.class));
    }

    /**
     * 正常回补：存在扣减流水且未回补过时加回库存并记录回补流水。
     */
    @Test
    @DisplayName("正常回补：加回库存并记录回补流水")
    void restoreSuccess() {
        when(stockMapper.selectByWindowProductForUpdate(WINDOW_ID, PRODUCT_ID))
                .thenReturn(buildStock(8, 1));
        // 回补流水不存在，扣减流水存在。
        when(stockFlowMapper.countByBiz(eq(StockFlowTypes.ORDER_RESTORE), anyString(), anyLong()))
                .thenReturn(0);
        when(stockFlowMapper.countByBiz(eq(StockFlowTypes.ORDER_DEDUCT), anyString(), anyLong()))
                .thenReturn(1);
        when(stockMapper.restoreQuantity(STOCK_ID, 2)).thenReturn(1);

        stockService.restore(buildRestoreRequest(2));

        verify(stockMapper).restoreQuantity(STOCK_ID, 2);
        ArgumentCaptor<StockFlowDO> flowCaptor = ArgumentCaptor.forClass(StockFlowDO.class);
        verify(stockFlowMapper).insert(flowCaptor.capture());
        StockFlowDO flow = flowCaptor.getValue();
        assertEquals(StockFlowTypes.ORDER_RESTORE, flow.getBizType());
        assertEquals(2, flow.getChangeQty());
        assertEquals(8, flow.getBeforeQty());
        assertEquals(10, flow.getAfterQty());
        // 回补后把快照对齐到数据库值。
        verify(stockRedisScript).resetSnapshot(WINDOW_ID, PRODUCT_ID, 10);
    }

    /**
     * 库存查询：无记录时返回 null，有记录时返回数据库值。
     */
    @Test
    @DisplayName("库存查询：以数据库可售数量为准")
    void getStock() {
        when(stockMapper.selectByWindowProduct(WINDOW_ID, PRODUCT_ID))
                .thenReturn(buildStock(7, 3));

        StockVO stockVO = stockService.getStock(WINDOW_ID, PRODUCT_ID);

        assertEquals(STOCK_ID, stockVO.getStockId());
        assertEquals(7, stockVO.getAvailableQty());
        assertEquals(10, stockVO.getTotalQty());
        assertTrue(stockVO.getWindowId().equals(WINDOW_ID));
    }

    /** 按窗口一次性读取有效库存，避免逐个菜品查询。 */
    @Test
    void shouldQueryWindowStockInOneDatabaseCall() {
        when(stockMapper.selectByWindow(WINDOW_ID)).thenReturn(List.of(buildStock(7, 3)));
        List<StockVO> result = stockService.getWindowStocks(WINDOW_ID);
        assertEquals(1, result.size());
        assertEquals(PRODUCT_ID, result.getFirst().getProductId());
        assertEquals(7, result.getFirst().getAvailableQty());
        verify(stockMapper).selectByWindow(WINDOW_ID);
        verify(stockMapper, never()).selectByWindowProduct(anyLong(), anyLong());
    }

    /** 构造指定可售数量及版本号的库存记录。 */
    private StockDO buildStock(int availableQty, int version) {
        StockDO stock = new StockDO();
        stock.setStockId(STOCK_ID);
        stock.setWindowId(WINDOW_ID);
        stock.setProductId(PRODUCT_ID);
        stock.setTotalQty(10);
        stock.setAvailableQty(availableQty);
        stock.setVersion(version);
        stock.setIsDeleted(0);
        return stock;
    }

    /**
     * 构造扣减请求。
     *
     * @param quantity 扣减数量
     * @return 扣减请求
     */
    private StockDeductRequest buildDeductRequest(int quantity) {
        return StockDeductRequest.builder()
                .bizRef(ORDER_NO)
                .windowId(WINDOW_ID)
                .items(buildItems(quantity))
                .operatorId(9001L)
                .remark("下单扣减")
                .build();
    }

    /**
     * 构造回补请求。
     *
     * @param quantity 回补数量
     * @return 回补请求
     */
    private StockRestoreRequest buildRestoreRequest(int quantity) {
        return StockRestoreRequest.builder()
                .bizRef(ORDER_NO)
                .windowId(WINDOW_ID)
                .items(buildItems(quantity))
                .operatorId(9001L)
                .remark("取消回补")
                .build();
    }

    /**
     * 构造单行菜品明细。
     *
     * @param quantity 数量
     * @return 明细集合
     */
    private List<StockItemRequest> buildItems(int quantity) {
        List<StockItemRequest> items = new ArrayList<>();
        items.add(StockItemRequest.builder()
                .productId(PRODUCT_ID)
                .quantity(quantity)
                .build());
        return items;
    }
}

package com.hbue.ordering.statistics.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.order.api.vo.OrderPaidSalesSnapshotVO;
import com.hbue.ordering.statistics.mapper.SalesProjectionMapper;
import com.hbue.ordering.statistics.messaging.SalesPaidEvent;
import com.hbue.ordering.statistics.model.SalesEventDO;
import com.hbue.ordering.statistics.model.SalesEventItemDO;
import com.hbue.ordering.statistics.model.SalesProductTotalDO;
import com.hbue.ordering.statistics.model.SalesWindowTotalDO;
import com.hbue.ordering.statistics.service.SalesProjectionService;
import com.hbue.ordering.statistics.vo.WindowMonthlySalesVO;
import com.hbue.ordering.statistics.vo.WindowProductMonthlySalesVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 销量投影的实现：MySQL 事实与汇总的唯一写入口，同时承载月度汇总的读取装配。
 *
 * <p>数据库唯一键负责跨实例幂等；仅首次插入事件时增加汇总。重复消息必须与既有快照一致，
 * 否则抛出异常交由 MQ 重试和死信排查，避免把不同业务载荷误当成正常重复。</p>
 *
 * <p>读取方法放在本类而不是编排层，是为了让"投影数据只能经投影服务访问"这条边界成立：
 * 编排层不再直接注入 Mapper，避免同一模块出现第二个数据访问入口。</p>
 */
@Service
public class SalesProjectionServiceImpl implements SalesProjectionService {

    /**
     * 累计（全时段）汇总的周期类型标识，与写入时的 period_type 列取值保持一致。
     */
    private static final String ALL_PERIOD = "ALL";

    /**
     * 累计（全时段）汇总的周期键，与 ALL_PERIOD 配对使用。
     */
    private static final String ALL_KEY = "ALLTIME";

    /**
     * Redis 整数字符串可精确表示的上限；超过该值再累加会丢精度，必须在写入前拒绝。
     */
    private static final long MAX_EXACT_REDIS_INTEGER = 9_007_199_254_740_991L;

    /**
     * 业务月份归属时区；固定为东八区，避免容器默认时区变化导致跨月错位。
     */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 只访问 statistics_db 的数据访问对象。
     */
    private final SalesProjectionMapper mapper;

    /**
     * 注入只负责本地 MySQL 的 Mapper；Redis 更新由榜单缓存服务在事务提交后执行。
     *
     * @param mapper 销量事实与汇总的数据访问对象
     */
    public SalesProjectionServiceImpl(SalesProjectionMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询稳定事件键是否已通过历史迁移或先前消费写入，用于跳过无需再查订单的旧消息。
     */
    @Override
    public boolean alreadyRecorded(String eventId) {
        return mapper.selectEvent(eventId) != null;
    }

    /**
     * 原子持久化已支付快照，并返回提交后的 MySQL 投影版本。
     *
     * <p>事务边界覆盖事件头、明细、窗口汇总、菜品汇总与版本递增五步，任一步失败整体回滚，
     * 保证 MySQL 中永不出现"有事件头但汇总缺行"的中间态。</p>
     */
    @Override
    @Transactional
    public long record(SalesPaidEvent event, OrderPaidSalesSnapshotVO snapshot) {
        validateSnapshot(event, snapshot);
        String monthKey = YearMonth.from(snapshot.paidAt()).toString();
        SalesEventDO header = new SalesEventDO();
        header.setEventId(event.eventId());
        header.setOrderId(snapshot.orderId());
        header.setWindowId(snapshot.windowId());
        header.setPaidAt(snapshot.paidAt());
        header.setEventVersion(event.version());

        if (mapper.insertEventIfAbsent(header) == 1) {
            List<SalesEventItemDO> details = toRows(event.eventId(), snapshot.items());
            if (mapper.insertEventItems(details) != details.size()) {
                throw new IllegalStateException("销量事件明细未完整保存，事务必须回滚");
            }
            addTotals(snapshot.windowId(), monthKey, details);
            if (mapper.incrementProjectionVersion() != 1) {
                throw new IllegalStateException("销量投影版本行缺失，事务必须回滚");
            }
        } else {
            // 唯一键冲突本身不能证明消息是安全重放；对比支付时间、窗口及逐行快照后才允许确认。
            verifyDuplicate(header, toRows(event.eventId(), snapshot.items()));
        }

        Long version = mapper.selectProjectionVersion();
        if (version == null || version < 0) throw new IllegalStateException("销量投影版本不可读取");
        return version;
    }

    /**
     * 批量读取所有窗口当前自然月的总份数。
     *
     * <p>只读本地汇总表，不跨服务调用；没有销量的窗口不会出现在结果中，由前端自行补零。</p>
     */
    @Override
    public WindowMonthlySalesVO listWindowMonthlySales() {
        String month = currentMonthKey();
        List<SalesWindowTotalDO> rows = mapper.selectMonthlyWindowTotals(month);
        List<WindowMonthlySalesVO.Item> items = new ArrayList<>();
        if (rows != null) {
            for (SalesWindowTotalDO row : rows) {
                items.add(new WindowMonthlySalesVO.Item(row.getWindowId(), row.getPaidQuantity()));
            }
        }
        return new WindowMonthlySalesVO(items);
    }

    /**
     * 读取单个窗口的自然月总份数及其菜品月销量。
     *
     * <p>窗口编号在读取边界校验，非法值直接返回 400，避免生成全表扫描条件。</p>
     */
    @Override
    public WindowProductMonthlySalesVO getWindowMonthlySales(Long windowId) {
        if (windowId == null || windowId <= 0) throw new BusinessException(CommonErrorCode.BAD_REQUEST, "窗口编号无效");
        String month = currentMonthKey();
        SalesWindowTotalDO window = mapper.selectMonthlyWindowTotal(month, windowId);
        long windowSales = window == null ? 0 : window.getPaidQuantity();
        List<SalesProductTotalDO> rows = mapper.selectMonthlyProductTotals(month, windowId);
        List<WindowProductMonthlySalesVO.ProductItem> products = new ArrayList<>();
        if (rows != null) {
            for (SalesProductTotalDO row : rows) {
                products.add(new WindowProductMonthlySalesVO.ProductItem(row.getProductId(), row.getPaidQuantity()));
            }
        }
        return new WindowProductMonthlySalesVO(windowId, windowSales, products);
    }

    /**
     * 当前月份由部署时区明确固定为 Asia/Shanghai，避免服务主机默认时区变化导致跨月错位。
     */
    private String currentMonthKey() {
        return YearMonth.now(BUSINESS_ZONE).toString();
    }

    /**
     * 把消息明细转换为统计库的事实行，保留每一行的订单明细标识。
     */
    private List<SalesEventItemDO> toRows(String eventId, List<OrderPaidSalesSnapshotVO.Item> items) {
        List<SalesEventItemDO> rows = new ArrayList<>();
        for (OrderPaidSalesSnapshotVO.Item item : items) {
            SalesEventItemDO row = new SalesEventItemDO();
            row.setEventId(eventId);
            row.setItemId(item.itemId());
            row.setProductId(item.productId());
            row.setProductName(item.productName());
            row.setQuantity(item.quantity());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 在同一本地事务中增加 ALL 与支付自然月两套窗口和菜品汇总。
     */
    private void addTotals(Long windowId, String monthKey, List<SalesEventItemDO> rows) {
        Map<Long, ProductGroup> products = new HashMap<>();
        long orderQuantity = 0;
        for (SalesEventItemDO row : rows) {
            ProductGroup group = products.computeIfAbsent(row.getProductId(), ignored -> new ProductGroup());
            group.quantity = Math.addExact(group.quantity, row.getQuantity());
            orderQuantity = Math.addExact(orderQuantity, row.getQuantity());
            if (group.latest == null || row.getItemId() > group.latest.getItemId()) group.latest = row;
        }
        if (orderQuantity <= 0 || orderQuantity > MAX_EXACT_REDIS_INTEGER) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "订单销量超出可统计范围");
        }
        mapper.addWindowTotal(ALL_PERIOD, ALL_KEY, windowId, orderQuantity);
        mapper.addWindowTotal("MONTH", monthKey, windowId, orderQuantity);
        for (Map.Entry<Long, ProductGroup> entry : products.entrySet()) {
            ProductGroup group = entry.getValue();
            if (group.quantity > MAX_EXACT_REDIS_INTEGER)
                throw new BusinessException(CommonErrorCode.BAD_REQUEST, "菜品销量超出可统计范围");
            SalesEventItemDO latest = group.latest;
            mapper.addProductTotal(ALL_PERIOD, ALL_KEY, windowId, entry.getKey(), latest.getProductName(), latest.getItemId(), group.quantity);
            mapper.addProductTotal("MONTH", monthKey, windowId, entry.getKey(), latest.getProductName(), latest.getItemId(), group.quantity);
        }
    }

    /**
     * 验证订单身份与所有原始菜品快照；不一致属于数据冲突而不是可吞掉的重复消息。
     */
    private void verifyDuplicate(SalesEventDO incoming, List<SalesEventItemDO> incomingItems) {
        SalesEventDO stored = mapper.selectEvent(incoming.getEventId());
        if (stored == null || !stored.getOrderId().equals(incoming.getOrderId())
                || !stored.getWindowId().equals(incoming.getWindowId()) || !stored.getPaidAt().equals(incoming.getPaidAt())) {
            throw new IllegalStateException("同一销量事件键对应了不同支付事实");
        }
        List<SalesEventItemDO> storedItems = mapper.selectEventItems(incoming.getEventId());
        incomingItems.sort(Comparator.comparing(SalesEventItemDO::getItemId));
        if (storedItems.size() != incomingItems.size())
            throw new IllegalStateException("重复销量消息的订单明细数量不一致");
        for (int i = 0; i < storedItems.size(); i++) {
            SalesEventItemDO left = storedItems.get(i);
            SalesEventItemDO right = incomingItems.get(i);
            if (!left.getItemId().equals(right.getItemId()) || !left.getProductId().equals(right.getProductId())
                    || !left.getProductName().equals(right.getProductName()) || !left.getQuantity().equals(right.getQuantity())) {
                throw new IllegalStateException("重复销量消息的菜品快照不一致");
            }
        }
    }

    /**
     * 在写入边界检查订单、时间和每条购买明细，拒绝空事实或不稳定事件键。
     */
    private void validateSnapshot(SalesPaidEvent event, OrderPaidSalesSnapshotVO snapshot) {
        if (event == null || !event.valid() || snapshot == null || snapshot.orderId() == null
                || !event.orderId().equals(snapshot.orderId()) || snapshot.paidAt() == null
                || snapshot.windowId() == null || snapshot.windowId() <= 0 || snapshot.items() == null || snapshot.items().isEmpty()) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "已支付销量快照不完整");
        }
        for (OrderPaidSalesSnapshotVO.Item item : snapshot.items()) {
            if (item == null || item.itemId() == null || item.itemId() <= 0 || item.productId() == null || item.productId() <= 0
                    || item.productName() == null || item.productName().isBlank() || item.productName().length() > 255
                    || item.quantity() == null || item.quantity() <= 0) {
                throw new BusinessException(CommonErrorCode.BAD_REQUEST, "已支付菜品快照格式不正确");
            }
        }
    }

    /**
     * 同一菜品行的汇总累加器。
     *
     * <p>同一订单可能出现重复菜品行，需要先汇总份数；{@code latest} 保留 itemId 最大的行，
     * 使汇总表里的展示名称总是取最新一次订单明细的名称快照。</p>
     */
    private static final class ProductGroup {

        /**
         * 该菜品在本订单中的总份数。
         */
        private long quantity;

        /**
         * itemId 最大的明细行，用于决定展示名称。
         */
        private SalesEventItemDO latest;
    }
}

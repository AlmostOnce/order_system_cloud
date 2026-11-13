package com.hbue.ordering.statistics.service;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.order.api.vo.OrderPaidSalesSnapshotVO;
import com.hbue.ordering.statistics.mapper.SalesProjectionMapper;
import com.hbue.ordering.statistics.model.SalesEventDO;
import com.hbue.ordering.statistics.model.SalesEventItemDO;
import com.hbue.ordering.statistics.messaging.SalesPaidEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 在 statistics_db 内原子保存事件事实、订单菜品明细与累计/月度汇总。
 *
 * <p>数据库唯一键负责跨实例幂等；仅首次插入事件时增加汇总，重复消息必须与既存快照一致，否则抛出异常交由
 * MQ 重试和死信排查，避免把不同业务载荷误当成正常重复。</p>
 */
@Service
public class SalesProjectionService {
    private static final String ALL_PERIOD = "ALL";
    private static final String ALL_KEY = "ALLTIME";
    private static final long MAX_EXACT_REDIS_INTEGER = 9_007_199_254_740_991L;
    private final SalesProjectionMapper mapper;

    /** 注入只负责本地 MySQL 的 Mapper；Redis 更新在此事务提交后执行。 */
    public SalesProjectionService(SalesProjectionMapper mapper) {
        this.mapper = mapper;
    }

    /** 查询稳定事件键是否已通过历史迁移或先前消费写入，用于跳过无需再查订单的旧消息。 */
    public boolean alreadyRecorded(String eventId) {
        return mapper.selectEvent(eventId) != null;
    }

    /**
     * 原子持久化已支付快照，并返回提交后的 MySQL 投影版本。
     *
     * @param event RocketMQ 上收到的事件版本
     * @param snapshot 已由 V2 消息携带或由订单内部接口补齐的购买事实
     * @return 当前权威投影版本，重复投递不会递增该值
     */
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

    /** 把消息明细转换为统计库的事实行，保留每一行的订单明细标识。 */
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

    /** 在同一本地事务中增加 ALL 与支付自然月两套窗口和菜品汇总。 */
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
            if (group.quantity > MAX_EXACT_REDIS_INTEGER) throw new BusinessException(CommonErrorCode.BAD_REQUEST, "菜品销量超出可统计范围");
            SalesEventItemDO latest = group.latest;
            mapper.addProductTotal(ALL_PERIOD, ALL_KEY, windowId, entry.getKey(), latest.getProductName(), latest.getItemId(), group.quantity);
            mapper.addProductTotal("MONTH", monthKey, windowId, entry.getKey(), latest.getProductName(), latest.getItemId(), group.quantity);
        }
    }

    /** 验证订单身份与所有原始菜品快照；不一致属于数据冲突而不是可吞掉的重复消息。 */
    private void verifyDuplicate(SalesEventDO incoming, List<SalesEventItemDO> incomingItems) {
        SalesEventDO stored = mapper.selectEvent(incoming.getEventId());
        if (stored == null || !stored.getOrderId().equals(incoming.getOrderId())
                || !stored.getWindowId().equals(incoming.getWindowId()) || !stored.getPaidAt().equals(incoming.getPaidAt())) {
            throw new IllegalStateException("同一销量事件键对应了不同支付事实");
        }
        List<SalesEventItemDO> storedItems = mapper.selectEventItems(incoming.getEventId());
        incomingItems.sort(Comparator.comparing(SalesEventItemDO::getItemId));
        if (storedItems.size() != incomingItems.size()) throw new IllegalStateException("重复销量消息的订单明细数量不一致");
        for (int i = 0; i < storedItems.size(); i++) {
            SalesEventItemDO left = storedItems.get(i);
            SalesEventItemDO right = incomingItems.get(i);
            if (!left.getItemId().equals(right.getItemId()) || !left.getProductId().equals(right.getProductId())
                    || !left.getProductName().equals(right.getProductName()) || !left.getQuantity().equals(right.getQuantity())) {
                throw new IllegalStateException("重复销量消息的菜品快照不一致");
            }
        }
    }

    /** 在写入边界检查订单、时间和每条购买明细，拒绝空事实或不稳定事件键。 */
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

    /** 同一订单中若出现重复菜品行，先汇总份数并保留最新的名称快照。 */
    private static final class ProductGroup {
        private long quantity;
        private SalesEventItemDO latest;
    }
}

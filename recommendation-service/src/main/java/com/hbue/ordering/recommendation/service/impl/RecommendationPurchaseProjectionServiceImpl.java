package com.hbue.ordering.recommendation.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.order.api.recommendation.RecommendationPurchaseSnapshotEvent;
import com.hbue.ordering.recommendation.mapper.RecommendationOrderItemMapper;
import com.hbue.ordering.recommendation.mapper.RecommendationOrderSnapshotMapper;
import com.hbue.ordering.recommendation.model.RecommendationOrderItemDO;
import com.hbue.ordering.recommendation.model.RecommendationOrderSnapshotDO;
import com.hbue.ordering.recommendation.service.RecommendationPurchaseProjectionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.Set;

/** 把订单服务支付和成功退款快照投影到推荐库，保持源明细及退款定位不丢失。 */
@Service
public class RecommendationPurchaseProjectionServiceImpl implements RecommendationPurchaseProjectionService {
    private final RecommendationOrderSnapshotMapper orders;
    private final RecommendationOrderItemMapper items;
    private final ObjectMapper json;

    /** 注入本服务快照表与 JSON 解析器；方法事务确保头版本和所有明细一起提交。 */
    public RecommendationPurchaseProjectionServiceImpl(RecommendationOrderSnapshotMapper orders,
            RecommendationOrderItemMapper items, ObjectMapper json) {
        this.orders = orders;
        this.items = items;
        this.json = json;
    }

    /**
     * 按源订单版本串行应用完整快照。旧事件及重复事件为无操作；新版本任一明细落库失败时
     * 本地事务回滚，保留上一次完整有效快照，交由 RocketMQ 重投。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void apply(RecommendationPurchaseSnapshotEvent event) {
        if (event == null || !event.valid()) throw new IllegalArgumentException("推荐订单事件格式无效");
        for (RecommendationPurchaseSnapshotEvent.Item item : event.items()) validateItem(item);

        orders.insertLockRow(event.orderId(), event.userId(), event.windowId(), event.paidAt());
        RecommendationOrderSnapshotDO current = orders.selectForUpdate(event.orderId());
        if (current == null) throw new IllegalStateException("推荐订单快照锁行缺失");
        if (current.getOrderVersion() >= event.orderVersion()) return;
        if (current.getOrderVersion() > 0 && (!event.userId().equals(current.getUserId())
                || !event.windowId().equals(current.getWindowId()) || !event.paidAt().equals(current.getPaidAt()))) {
            throw new IllegalArgumentException("同一订单的新快照改变了固定归属信息");
        }

        if (orders.updateSnapshot(event.orderId(), event.userId(), event.windowId(),
                event.orderVersion(), event.paidAt()) != 1) {
            throw new IllegalStateException("推荐订单头快照写入失败");
        }
        items.deleteByOrderId(event.orderId());
        for (RecommendationPurchaseSnapshotEvent.Item source : event.items()) {
            RecommendationOrderItemDO row = new RecommendationOrderItemDO();
            row.setItemId(source.itemId());
            row.setOrderId(event.orderId());
            row.setProductId(source.productId());
            row.setSelectionKey(source.selectionKey());
            row.setSpecificationTextSnapshot(source.specificationText());
            row.setSpecificationSelectionSnapshot(source.specificationSelectionSnapshot());
            row.setPurchasedQuantity(source.purchasedQuantity());
            row.setRefundedQuantity(source.refundedQuantity());
            if (items.insert(row) != 1) throw new IllegalStateException("推荐订单明细快照写入失败");
        }
    }

    /** 校验服务端规格快照与选择键一致；坏消息不能产生伪口味标签或部分兴趣更新。 */
    private void validateItem(RecommendationPurchaseSnapshotEvent.Item item) {
        try {
            JsonNode root = json.readTree(item.specificationSelectionSnapshot());
            if (root == null || !root.isArray() || root.size() > 10) {
                throw new IllegalArgumentException("规格结构快照必须为最多10项的数组");
            }
            Set<Long> groupIds = new HashSet<>();
            Set<Long> optionIds = new HashSet<>();
            for (JsonNode node : root) {
                long groupId = node.path("groupId").asLong(-1);
                long optionId = node.path("optionId").asLong(-1);
                String groupName = node.path("groupName").asText("");
                String optionName = node.path("optionName").asText("");
                if (groupId <= 0 || optionId <= 0 || groupName.isBlank() || optionName.isBlank()
                        || !groupIds.add(groupId) || !optionIds.add(optionId)) {
                    throw new IllegalArgumentException("规格结构快照存在空值或重复组/选项");
                }
            }
            String expected = root.isEmpty() ? "base" : optionIds.stream().sorted()
                    .map(String::valueOf).collect(java.util.stream.Collectors.joining("-"));
            if (!expected.equals(item.selectionKey())) {
                throw new IllegalArgumentException("规格结构快照与订单选择键不一致");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("规格结构快照无法解析", exception);
        }
    }
}

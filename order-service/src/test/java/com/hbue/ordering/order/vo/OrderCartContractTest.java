package com.hbue.ordering.order.vo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.api.vo.OrderCartSnapshotVO;
import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.model.OrderItemDO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证订单生产方的真实详情响应能够被购物车只读契约消费，防止字段漂移或 BIGINT 丢精度。 */
class OrderCartContractTest {
    /**
     * 用实际 DO→VO→统一响应的 JSON 校验契约，不使用手写的缩减响应替代生产格式。
     * @throws Exception JSON 序列化或契约解析失败时抛出
     */
    @Test
    void shouldReadRealOrderDetailThroughCartContract() throws Exception {
        OrderDO order = new OrderDO();
        order.setOrderId(123L);
        order.setUserId(5L);
        order.setWindowId(9007199254740993L);
        order.setStatus("待支付");
        order.setCreatedAt(LocalDateTime.of(2026, 10, 17, 12, 0));
        OrderItemDO item = new OrderItemDO();
        item.setProductId(9007199254740995L);
        item.setQuantity(2);
        item.setProductNameSnapshot("鸡肉饭");
        item.setUnitPriceSnapshot(new BigDecimal("18.50"));

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String json = mapper.writeValueAsString(ApiResponse.success(OrderDetailVO.from(order, List.of(item))));
        ApiResponse<OrderCartSnapshotVO> response = mapper.readValue(json, new TypeReference<>() { });
        assertEquals("COMMON_000", response.getCode());
        assertEquals(123L, response.getData().orderId());
        assertEquals(5L, response.getData().userId());
        assertEquals(9007199254740993L, response.getData().windowId());
        assertEquals(9007199254740995L, response.getData().items().getFirst().productId());
        assertEquals(2, response.getData().items().getFirst().quantity());
    }
}

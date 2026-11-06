package com.hbue.ordering.order.service.impl;

import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.order.mapper.OrderMapper;
import com.hbue.ordering.order.model.OrderDO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 订单分页边界测试：只验证真实Service的状态映射、归属校验及分页上限，不连接数据库。 */
class OrderPaginationTest {
    private final OrderMapper mapper = mock(OrderMapper.class);
    private final OrderServiceImpl service = new OrderServiceImpl(null, null, null, null, null, null, null, "test");

    /** 注入Mapper替身，避免启动Spring或访问其他业务服务。 */
    OrderPaginationTest() {
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    /** 构造最小订单头；列表不调用订单明细查询或逐笔远程查询。 */
    private OrderDO order(long id) {
        OrderDO order = new OrderDO();
        order.setOrderId(id);
        order.setUserId(7L);
        order.setCreatedAt(java.time.LocalDateTime.of(2026, 11, 6, 10, 0));
        order.setStatus("已完成");
        return order;
    }

    /** 多取一条只用于hasMore，响应不能把第limit+1条返回给客户端。 */
    @Test void boundedQueryReturnsCursorWithoutCountingAllOrders() {
        when(mapper.selectUserPage(7L, null, null, 3)).thenReturn(List.of(order(10), order(9), order(8)));
        var page = service.pageByUserId(7L, "all", null, 2);
        assertEquals(List.of(10L, 9L), page.items().stream().map(x -> x.getOrderId()).toList());
        assertEquals("9", page.nextCursor());
        assertTrue(page.hasMore());
        verify(mapper).selectUserPage(7L, null, null, 3);
    }

    /** 五组状态在后端映射，不允许页面下载全部订单后再筛选。 */
    @Test void allFiltersReachDatabaseWithAuthenticatedUserScope() {
        String[] filters = {"all", "pending", "active", "completed", "cancelled"};
        List<List<String>> values = java.util.Arrays.asList(null, List.of("待支付"), List.of("已支付", "制作中", "待取货"), List.of("已完成", "已退款"), List.of("已取消"));
        for (int i = 0; i < filters.length; i++) {
            when(mapper.selectUserPage(7L, values.get(i), null, 21)).thenReturn(List.of());
            var page = service.pageByUserId(7L, filters[i], null, 20);
            assertFalse(page.hasMore());
            assertNull(page.nextCursor());
            verify(mapper).selectUserPage(7L, values.get(i), null, 21);
        }
    }

    /** 非法参数必须在查询列表之前拒绝，防止任意limit或超长游标扩大数据库查询。 */
    @Test void invalidParametersCannotQueryPage() {
        assertThrows(BusinessException.class, () -> service.pageByUserId(7L, "unknown", null, 20));
        assertThrows(BusinessException.class, () -> service.pageByUserId(7L, "all", null, 51));
        assertThrows(BusinessException.class, () -> service.pageByUserId(7L, "all", "-1", 20));
        assertThrows(BusinessException.class, () -> service.pageByUserId(7L, "all", "9223372036854775808", 20));
        verifyNoInteractions(mapper);
    }

    /** 他人的游标与不存在的游标统一不可见，不借编号泄漏其他人的订单。 */
    @Test void foreignCursorIsRejectedBeforePageQuery() {
        when(mapper.existsForUserOrder(7L, 90L)).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.pageByUserId(7L, "all", "90", 20));
        verify(mapper, never()).selectUserPage(any(), any(), any(), anyInt());
    }

    /** 游标按不可变主键向后查询，列表顶部新插入的订单不会移动已加载页的边界。 */
    @Test void cursorAndFilterStayBoundToCurrentUser() {
        when(mapper.existsForUserOrder(7L, 90L)).thenReturn(true);
        when(mapper.selectUserPage(7L, List.of("待支付"), 90L, 21)).thenReturn(List.of(order(89)));
        var page = service.pageByUserId(7L, "pending", "90", 20);
        assertEquals(89L, page.items().getFirst().getOrderId());
        assertFalse(page.hasMore());
    }
}

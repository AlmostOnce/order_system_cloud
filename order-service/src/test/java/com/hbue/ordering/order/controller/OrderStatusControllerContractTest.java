package com.hbue.ordering.order.controller;

import com.hbue.ordering.order.dto.request.OrderStatusUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 订单状态接口路径和角色约束测试。
 *
 * @author order-system
 * @date 2026-09-28
 */
class OrderStatusControllerContractTest {

    /**
     * 顾客取消接口必须处于订单路由下并限制为顾客角色。
     *
     * @throws NoSuchMethodException 接口方法不存在时抛出
     */
    @Test
    void shouldExposeCustomerCancellationEndpoint() throws NoSuchMethodException {
        Method method = OrderController.class.getMethod(
                "cancelOrder",
                org.springframework.security.oauth2.jwt.Jwt.class,
                Long.class
        );

        assertEquals("/orders", OrderController.class
                .getAnnotation(RequestMapping.class).value()[0]);
        assertEquals("/{orderId}/cancel",
                method.getAnnotation(PatchMapping.class).value()[0]);
        assertEquals("hasRole('CUSTOMER')",
                method.getAnnotation(PreAuthorize.class).value());
    }

    /**
     * 管理员状态接口必须使用独立路径并限制为管理员角色。
     *
     * @throws NoSuchMethodException 接口方法不存在时抛出
     */
    @Test
    void shouldExposeAdminStatusEndpoint() throws NoSuchMethodException {
        Method method = OrderController.class.getMethod(
                "updateStatusByAdmin",
                Long.class,
                OrderStatusUpdateRequest.class
        );

        assertEquals("/admin/{orderId}/status",
                method.getAnnotation(PatchMapping.class).value()[0]);
        assertEquals("hasRole('ADMIN')",
                method.getAnnotation(PreAuthorize.class).value());
    }
}

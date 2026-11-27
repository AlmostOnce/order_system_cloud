package com.hbue.ordering.order.controller;

import org.junit.jupiter.api.Test;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 订单队列与经营报表接口契约测试。
 *
 * <p>角色表达式、参数名与日期格式是前后端联调的稳定契约。这里用反射直接断言注解，
 * 不启动 Spring 容器，也不连接数据库。</p>
 *
 * @author order-system
 * @date 2026-11-28
 */
class OrderAdminContractTest {

    /**
     * 订单队列只允许档口负责人与餐厅经理访问。
     */
    @Test
    void shouldRestrictQueueToBusinessRoles() throws NoSuchMethodException {
        assertEquals(
                "hasAnyRole('STALL_MANAGER','RESTAURANT_MANAGER')",
                OrderController.class
                        .getMethod("windowQueue", Jwt.class, Long.class, String.class,
                                String.class, int.class)
                        .getAnnotation(PreAuthorize.class)
                        .value()
        );
    }

    /**
     * 经营报表允许三种业务角色，系统管理员不参与。
     */
    @Test
    void shouldAllowThreeBusinessRolesForReport() throws NoSuchMethodException {
        assertEquals(
                "hasAnyRole('STALL_MANAGER','RESTAURANT_MANAGER','GENERAL_MANAGER')",
                OrderController.class
                        .getMethod("salesReport", Jwt.class, Long.class,
                                LocalDateTime.class, LocalDateTime.class)
                        .getAnnotation(PreAuthorize.class)
                        .value()
        );
    }

    /**
     * 队列参数名必须显式声明，windowId 不能省略。
     */
    @Test
    void shouldDeclareQueueParameterNames() throws NoSuchMethodException {
        var parameters = OrderController.class
                .getMethod("windowQueue", Jwt.class, Long.class, String.class,
                        String.class, int.class)
                .getParameters();
        assertEquals("windowId", parameters[1].getAnnotation(RequestParam.class).name());
        assertTrue(parameters[1].getAnnotation(RequestParam.class).required());
        assertEquals("status", parameters[2].getAnnotation(RequestParam.class).name());
        assertEquals("all", parameters[2].getAnnotation(RequestParam.class).defaultValue());
        assertEquals("cursor", parameters[3].getAnnotation(RequestParam.class).name());
        assertEquals("limit", parameters[4].getAnnotation(RequestParam.class).name());
    }

    /**
     * 报表时间参数必须是 ISO 日期时间，避免不同时区格式混用。
     */
    @Test
    void shouldDeclareIsoDateTimeForReportRange() throws NoSuchMethodException {
        var parameters = OrderController.class
                .getMethod("salesReport", Jwt.class, Long.class,
                        LocalDateTime.class, LocalDateTime.class)
                .getParameters();
        assertEquals("windowId", parameters[1].getAnnotation(RequestParam.class).name());
        assertEquals(false, parameters[1].getAnnotation(RequestParam.class).required());
        assertEquals("from", parameters[2].getAnnotation(RequestParam.class).name());
        assertEquals("to", parameters[3].getAnnotation(RequestParam.class).name());
        assertEquals(DateTimeFormat.ISO.DATE_TIME,
                parameters[2].getAnnotation(DateTimeFormat.class).iso());
        assertEquals(DateTimeFormat.ISO.DATE_TIME,
                parameters[3].getAnnotation(DateTimeFormat.class).iso());
    }
}

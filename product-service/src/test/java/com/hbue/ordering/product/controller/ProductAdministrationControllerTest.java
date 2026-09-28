package com.hbue.ordering.product.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 商品目录管理接口的权限约定测试。
 *
 * @author order-system
 * @date 2026-09-28
 */
class ProductAdministrationControllerTest {

    /** 管理接口必须整体限制为 ADMIN 角色。 */
    @Test
    void shouldRestrictAllAdministrationEndpointsToAdminRole() {
        PreAuthorize authorization = ProductAdministrationController.class
                .getAnnotation(PreAuthorize.class);

        assertNotNull(authorization);
        assertEquals("hasRole('ADMIN')", authorization.value());
    }
}

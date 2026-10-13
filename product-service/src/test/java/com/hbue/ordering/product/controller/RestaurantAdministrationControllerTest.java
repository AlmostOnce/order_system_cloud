package com.hbue.ordering.product.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 餐厅管理接口的权限约定测试。
 *
 * @author order-system
 * @date 2026-10-13
 */
class RestaurantAdministrationControllerTest {

    /** 餐厅管理接口必须限制为餐厅经理与总经理。 */
    @Test
    void shouldRestrictRestaurantEndpointsToManagers() {
        PreAuthorize authorization = RestaurantAdministrationController.class
                .getAnnotation(PreAuthorize.class);

        assertNotNull(authorization);
        assertEquals(
                "hasAnyRole('RESTAURANT_MANAGER','GENERAL_MANAGER')",
                authorization.value()
        );
    }
}

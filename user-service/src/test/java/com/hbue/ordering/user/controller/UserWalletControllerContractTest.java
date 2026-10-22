package com.hbue.ordering.user.controller;

import com.hbue.ordering.user.dto.request.RechargeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestParam;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserWalletControllerContractTest {
    @Test
    void shouldRequireCustomerForRechargeAndAuthenticationForOwnProfile() throws NoSuchMethodException {
        assertEquals("hasRole('CUSTOMER')", UserWalletController.class
                .getMethod("recharge", Jwt.class, String.class, RechargeRequest.class)
                .getAnnotation(PreAuthorize.class).value());
        assertEquals("isAuthenticated()", UserWalletController.class
                .getMethod("profile", Jwt.class).getAnnotation(PreAuthorize.class).value());
        assertEquals("hasRole('CUSTOMER')", UserWalletController.class
                .getMethod("records", Jwt.class, String.class, Long.class, int.class)
                .getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void shouldDeclareQueryNamesWithoutDependingOnCompilerParameterMetadata() throws NoSuchMethodException {
        var parameters = UserWalletController.class
                .getMethod("records", Jwt.class, String.class, Long.class, int.class).getParameters();
        var type = parameters[1].getAnnotation(RequestParam.class);
        var beforeId = parameters[2].getAnnotation(RequestParam.class);
        var limit = parameters[3].getAnnotation(RequestParam.class);
        assertEquals("type", type.name());
        assertEquals(false, type.required());
        assertEquals("beforeId", beforeId.name());
        assertEquals(false, beforeId.required());
        assertEquals("limit", limit.name());
        assertEquals("20", limit.defaultValue());
    }
}

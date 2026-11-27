package com.hbue.ordering.user.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestParam;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 管理端账号检索接口契约测试。
 *
 * <p>角色表达式与查询参数名是前后端联调的稳定契约：角色放错会把账号目录暴露给
 * 业务角色，参数名缺失会在关闭编译参数元数据后导致启动失败。这里用反射直接
 * 断言注解，不启动 Spring 容器。</p>
 *
 * @author order-system
 * @date 2026-11-28
 */
class UserDirectoryAdministrationControllerContractTest {

    /**
     * 账号目录只允许系统管理员访问。
     */
    @Test
    void shouldRequireSystemAdminForDirectory() throws NoSuchMethodException {
        assertEquals("hasRole('SYSTEM_ADMIN')", UserDirectoryAdministrationController.class
                .getMethod("directory", Jwt.class, String.class, String.class,
                        Integer.class, String.class, int.class)
                .getAnnotation(PreAuthorize.class).value());
    }

    /**
     * 数据范围候选只允许餐厅经理与总经理访问。
     */
    @Test
    void shouldRequireBusinessRolesForScopeCandidates() throws NoSuchMethodException {
        assertEquals("hasAnyRole('RESTAURANT_MANAGER','GENERAL_MANAGER')",
                UserDirectoryAdministrationController.class
                        .getMethod("scopeCandidates", Jwt.class, String.class,
                                String.class, int.class)
                        .getAnnotation(PreAuthorize.class).value());
    }

    /**
     * 查询参数名与默认条数必须显式声明，不依赖编译期参数名。
     */
    @Test
    void shouldDeclareQueryParameterNames() throws NoSuchMethodException {
        var directoryParameters = UserDirectoryAdministrationController.class
                .getMethod("directory", Jwt.class, String.class, String.class,
                        Integer.class, String.class, int.class)
                .getParameters();
        assertEquals("keyword", directoryParameters[1]
                .getAnnotation(RequestParam.class).name());
        assertEquals(false, directoryParameters[1]
                .getAnnotation(RequestParam.class).required());
        assertEquals("roleCode", directoryParameters[2]
                .getAnnotation(RequestParam.class).name());
        assertEquals("status", directoryParameters[3]
                .getAnnotation(RequestParam.class).name());
        assertEquals("cursor", directoryParameters[4]
                .getAnnotation(RequestParam.class).name());
        assertEquals("size", directoryParameters[5]
                .getAnnotation(RequestParam.class).name());
        assertEquals("20", directoryParameters[5]
                .getAnnotation(RequestParam.class).defaultValue());

        var candidateParameters = UserDirectoryAdministrationController.class
                .getMethod("scopeCandidates", Jwt.class, String.class,
                        String.class, int.class)
                .getParameters();
        assertEquals("keyword", candidateParameters[1]
                .getAnnotation(RequestParam.class).name());
        assertEquals("cursor", candidateParameters[2]
                .getAnnotation(RequestParam.class).name());
        assertEquals("size", candidateParameters[3]
                .getAnnotation(RequestParam.class).name());
    }
}

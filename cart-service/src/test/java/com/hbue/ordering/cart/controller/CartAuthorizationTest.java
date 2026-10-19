package com.hbue.ordering.cart.controller;

import com.hbue.ordering.cart.config.CartSecurityConfig;
import com.hbue.ordering.cart.security.CartSecurityErrorHandler;
import com.hbue.ordering.cart.service.CartService;
import com.hbue.ordering.cart.service.CartCleanupService;
import com.hbue.ordering.cart.vo.CartCleanupVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 购物车入口的真实安全过滤链测试。
 *
 * <p>仅替换 JWT 解码器以隔离 Redis 和外置密钥，保留 Bearer Token 解析、角色转换、
 * 访问规则及安全错误处理，验证直连服务时同样受保护。</p>
 *
 * @author order-system
 * @date 2026-10-17
 */
@WebMvcTest({CartController.class, CartCleanupController.class})
@Import({CartSecurityConfig.class, CartSecurityErrorHandler.class})
class CartAuthorizationTest {

    /** 已登录顾客提交缺少快照的清理请求应返回 400，而不是没有该接口的 404。 */
    @Test
    void shouldValidateOrderCleanupSnapshot() throws Exception {
        when(jwtDecoder.decode("customer-token")).thenReturn(token("CUSTOMER"));
        mockMvc.perform(post("/cart/orders/123/cleanup")
                        .header("Authorization", "Bearer customer-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    /** 清理只使用 JWT 身份，并把删除数量按统一格式返回给调用者。 */
    @Test
    void shouldCleanUsingAuthenticatedIdentity() throws Exception {
        when(jwtDecoder.decode("customer-token")).thenReturn(token("CUSTOMER"));
        when(cleanupService.cleanup(eq(5L), eq(123L), any())).thenReturn(new CartCleanupVO(123L, 1, 0));
        mockMvc.perform(post("/cart/orders/123/cleanup").param("userId", "999")
                        .header("Authorization", "Bearer customer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"windowId\":1,\"items\":[{\"productId\":11,\"quantity\":2,\"revision\":\"original\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(123))
                .andExpect(jsonPath("$.data.removedCount").value(1));
        verify(cleanupService).cleanup(eq(5L), eq(123L), any());
    }

    /** 清理接口不允许游客或管理员角色绕过顾客权限。 */
    @Test
    void shouldRejectUnauthenticatedAndNonCustomerCleanup() throws Exception {
        mockMvc.perform(post("/cart/orders/123/cleanup")).andExpect(status().isUnauthorized());
        when(jwtDecoder.decode("admin-token")).thenReturn(token("SYSTEM_ADMIN"));
        mockMvc.perform(post("/cart/orders/123/cleanup")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cleanupService);
    }

    /** 明细中的版本缺失时必须执行嵌套校验，不能进入清理实现。 */
    @Test
    void shouldRejectMissingNestedRevision() throws Exception {
        when(jwtDecoder.decode("customer-token")).thenReturn(token("CUSTOMER"));
        mockMvc.perform(post("/cart/orders/123/cleanup")
                        .header("Authorization", "Bearer customer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"windowId\":1,\"items\":[{\"productId\":11,\"quantity\":2}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(cleanupService);
    }

    /** 模拟 Servlet 请求入口。 */
    @Autowired
    private MockMvc mockMvc;

    /** 替换外置密钥与 Redis 依赖的 JWT 解码器。 */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    /** 替换业务服务，认证授权用例不依赖商品服务或 Redis 内容。 */
    @MockitoBean
    private CartService cartService;
    /** 清理业务边界替身；此处验证真实入口的身份与参数处理。 */
    @MockitoBean
    private CartCleanupService cleanupService;

    /**
     * 未登录的购物车请求必须返回统一的 HTTP 401。
     *
     * @throws Exception 请求执行失败时抛出
     */
    @Test
    void shouldRejectMissingToken() throws Exception {
        // 不提交 Authorization，验证入口不会因 ping 是探测接口而放行。
        mockMvc.perform(get("/cart/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_401"));
    }

    /**
     * 无效 Bearer Token 必须经过相同的 JSON 错误处理。
     *
     * @throws Exception 请求执行失败时抛出
     */
    @Test
    void shouldRejectInvalidBearerWithUnifiedResponse() throws Exception {
        when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("invalid signature"));
        mockMvc.perform(get("/cart/ping").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_401"));
    }

    /**
     * 有效顾客角色可以访问购物车入口。
     *
     * @throws Exception 请求执行失败时抛出
     */
    @Test
    void shouldAcceptCustomer() throws Exception {
        when(jwtDecoder.decode("customer-token")).thenReturn(token("CUSTOMER"));
        mockMvc.perform(get("/cart/ping").header("Authorization", "Bearer customer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_000"))
                .andExpect(jsonPath("$.data").value("cart-service is running"));
    }

    /**
     * 系统管理员不因管理权限而自动获得顾客购物车权限。
     *
     * @throws Exception 请求执行失败时抛出
     */
    @Test
    void shouldRejectNonCustomerRole() throws Exception {
        when(jwtDecoder.decode("admin-token")).thenReturn(token("SYSTEM_ADMIN"));
        mockMvc.perform(get("/cart/ping").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON_403"));
    }

    /**
     * 查询参数中的其它用户 ID 不得覆盖 JWT 中的真实归属。
     *
     * @throws Exception 请求执行失败时抛出
     */
    @Test
    void shouldUseJwtIdentityInsteadOfClientUserId() throws Exception {
        when(jwtDecoder.decode("customer-token")).thenReturn(token("CUSTOMER"));
        mockMvc.perform(get("/cart").param("userId", "999")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isOk());
        verify(cartService).getCart(5L);
    }

    /**
     * 添加请求只把已验证的 JWT 身份传给业务服务。
     *
     * @throws Exception 请求执行失败时抛出
     */
    @Test
    void shouldBindAddRequestAndTrustedUserId() throws Exception {
        when(jwtDecoder.decode("customer-token")).thenReturn(token("CUSTOMER"));
        mockMvc.perform(post("/cart/windows/1/items").header("Authorization", "Bearer customer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":11,\"quantity\":2,\"userId\":999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_000"));
        verify(cartService).addItem(eq(5L), eq(1L), any());
    }

    /**
     * 数量为零应由请求校验返回 400，业务服务不会被调用。
     *
     * @throws Exception 请求执行失败时抛出
     */
    @Test
    void shouldRejectZeroQuantityBeforeService() throws Exception {
        when(jwtDecoder.decode("customer-token")).thenReturn(token("CUSTOMER"));
        mockMvc.perform(post("/cart/windows/1/items").header("Authorization", "Bearer customer-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":11,\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
        verifyNoInteractions(cartService);
    }

    /**
     * false 是有效的取消勾选状态，不应被当作缺失字段。
     *
     * @throws Exception 请求执行失败时抛出
     */
    @Test
    void shouldAcceptUncheckedState() throws Exception {
        when(jwtDecoder.decode("customer-token")).thenReturn(token("CUSTOMER"));
        mockMvc.perform(patch("/cart/windows/1/items/11/checked")
                        .header("Authorization", "Bearer customer-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"checked\":false}"))
                .andExpect(status().isOk());
        verify(cartService).updateChecked(5L, 1L, 11L, false);
    }

    /**
     * 路径 ID 无法转换为数字时属于客户端输入错误，应返回统一 400。
     *
     * @throws Exception 请求执行失败时抛出
     */
    @Test
    void shouldRejectNonNumericWindowId() throws Exception {
        when(jwtDecoder.decode("customer-token")).thenReturn(token("CUSTOMER"));
        mockMvc.perform(post("/cart/windows/not-a-number/items")
                        .header("Authorization", "Bearer customer-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":11,\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
        verifyNoInteractions(cartService);
    }

    /**
     * 创建指定角色的解码结果，供实际角色转换器处理。
     *
     * @param role JWT 角色编码
     * @return 有效期内的 JWT 测试主体
     */
    private Jwt token(String role) {
        // 用户身份采用 sub，权限采用 roles，与认证服务签发格式保持一致。
        return Jwt.withTokenValue("test-token").header("alg", "RS256")
                .subject("5").claim("jti", "cart-test-id").claim("roles", List.of(role))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    }
}

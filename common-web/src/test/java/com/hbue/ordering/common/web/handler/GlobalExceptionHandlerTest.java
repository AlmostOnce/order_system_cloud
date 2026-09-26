package com.hbue.ordering.common.web.handler;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局异常处理器测试。
 */
class GlobalExceptionHandlerTest {

    /**
     * 验证缺少必需请求体时按客户端错误返回 HTTP 400。
     *
     * @throws Exception MockMvc 请求执行异常
     */
    @Test
    void shouldReturnBadRequestWhenRequiredRequestBodyIsMissing()
            throws Exception {
        // 使用真实 Spring MVC 请求解析流程复现缺少请求体的情况。
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new RequestBodyController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // 缺少请求体属于客户端请求错误，应返回统一的 400 响应。
        mockMvc.perform(
                        post("/test/body")
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    /**
     * 验证服务不可用业务异常返回 HTTP 503，而不是系统内部错误。
     *
     * @throws Exception MockMvc 请求执行异常
     */
    @Test
    void shouldReturnServiceUnavailableWhenBusinessServiceIsUnavailable()
            throws Exception {
        // 使用真实 Spring MVC 异常解析流程验证业务异常对应的 HTTP 状态。
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new RequestBodyController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // 服务不可用必须对客户端呈现 503 和统一错误码。
        mockMvc.perform(get("/test/service-unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COMMON_503"));
    }

    /**
     * 验证请求状态冲突时返回 HTTP 409。
     *
     * @throws Exception MockMvc 请求执行异常
     */
    @Test
    void shouldReturnConflictWhenRequestConflictsWithExistingResource()
            throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new RequestBodyController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON_409"));
    }

    /**
     * 用于模拟要求 JSON 请求体的接口。
     */
    @RestController
    public static class RequestBodyController {

        /**
         * 接收必需的 JSON 请求体。
         *
         * @param requestBody JSON 请求体
         * @return 请求体内容
         */
        @PostMapping("/test/body")
        public String acceptBody(
                @RequestBody String requestBody
        ) {
            // 返回请求体，确保该测试接口本身保持最小化。
            return requestBody;
        }

        /**
         * 模拟下游服务不可用时抛出的业务异常。
         *
         * @return 此接口不会正常返回
         */
        @GetMapping("/test/service-unavailable")
        public String serviceUnavailable() {
            // 使用公共错误码模拟远程服务不可用。
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "用户服务暂时不可用"
            );
        }

        /**
         * 模拟幂等键已被不同请求占用。
         *
         * @return 此接口不会正常返回
         */
        @GetMapping("/test/conflict")
        public String conflict() {
            // 使用公共冲突错误码验证统一异常响应状态。
            throw new BusinessException(
                    CommonErrorCode.CONFLICT,
                    "请求与已有订单冲突"
            );
        }
    }
}

package com.hbue.ordering.product.sentinel;

import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Sentinel 拦截（限流/降级）统一返回响应。
 *
 * <p>Sentinel 默认对 {@link BlockException} 返回 429 纯文本，
 * 不符合本项目统一响应体；这里统一转换为 HTTP 503 + {@link ApiResponse}，
 * 与其它业务异常的响应格式保持一致。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
@Component
public class SentinelBlockExceptionHandler implements BlockExceptionHandler {

    /** 当前类的日志对象。 */
    private static final Logger log =
            LoggerFactory.getLogger(SentinelBlockExceptionHandler.class);

    /** JSON 序列化工具。 */
    private final ObjectMapper objectMapper;

    /**
     * 创建 Sentinel 拦截响应处理器。
     *
     * @param objectMapper JSON 序列化工具
     */
    public SentinelBlockExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 写入统一格式的服务暂不可用响应。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param resourceName 触发的 Sentinel 资源名
     * @param exception 拦截异常（限流或降级）
     * @throws IOException 响应写入异常
     */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            String resourceName,
            BlockException exception
    ) throws IOException {
        // 资源被限流或降级属于可预期结果，只记录 warn 且不打印堆栈。
        log.warn(
                "Sentinel 拦截请求，资源={}，类型={}",
                resourceName,
                exception.getClass().getSimpleName()
        );
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.failure(CommonErrorCode.SERVICE_UNAVAILABLE)
        );
    }
}

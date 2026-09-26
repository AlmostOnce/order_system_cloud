package com.hbue.ordering.common.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.error.ErrorCode;

import java.util.Objects;

/**
 * HTTP 接口统一返回对象。
 *
 * <p>该模块只提供统一响应对象，
 * 不依赖 Servlet Web，Gateway 和业务服务都可以使用。</p>
 *
 * @param <T> 返回数据类型
 * @author order-system
 * @date 2026-09-22
 */
public final class ApiResponse<T> {

    /**
     * 业务状态码。
     */
    private final String code;

    /**
     * 返回提示信息。
     */
    private final String message;

    /**
     * 实际业务数据。
     */
    private final T data;

    /**
     * 创建统一响应对象。
     *
     * @param code 业务状态码
     * @param message 返回提示信息
     * @param data 实际业务数据
     */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ApiResponse(
            @JsonProperty("code") String code,
            @JsonProperty("message") String message,
            @JsonProperty("data") T data
    ) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 创建成功响应。
     *
     * @param data 成功数据
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        // 使用统一成功状态码。
        return new ApiResponse<>(
                CommonErrorCode.SUCCESS.code(),
                CommonErrorCode.SUCCESS.message(),
                data
        );
    }

    /**
     * 创建失败响应。
     *
     * @param errorCode 错误码
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> failure(ErrorCode errorCode) {
        // 错误码不能为空。
        ErrorCode validErrorCode = Objects.requireNonNull(
                errorCode,
                "errorCode must not be null"
        );

        // 失败响应不返回业务数据。
        return new ApiResponse<>(
                validErrorCode.code(),
                validErrorCode.message(),
                null
        );
    }

    /**
     * 创建带自定义提示信息的失败响应。
     *
     * @param errorCode 错误码
     * @param message 自定义错误信息
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> failure(
            ErrorCode errorCode,
            String message
    ) {
        // 错误码不能为空。
        ErrorCode validErrorCode = Objects.requireNonNull(
                errorCode,
                "errorCode must not be null"
        );

        // 没有自定义信息时使用默认错误信息。
        String responseMessage = message == null || message.isBlank()
                ? validErrorCode.message()
                : message;

        // 失败响应不返回业务数据。
        return new ApiResponse<>(
                validErrorCode.code(),
                responseMessage,
                null
        );
    }

    /**
     * 获取业务状态码。
     *
     * @return 业务状态码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取返回提示信息。
     *
     * @return 返回提示信息
     */
    public String getMessage() {
        return message;
    }

    /**
     * 获取业务数据。
     *
     * @return 实际业务数据
     */
    public T getData() {
        return data;
    }
}

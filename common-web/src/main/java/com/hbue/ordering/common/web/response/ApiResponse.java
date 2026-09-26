package com.hbue.order.common.web.response;

import com.hbue.order.common.core.error.CommonErrorCode;
import com.hbue.order.common.core.error.ErrorCode;

import java.util.Objects;

/**
 * HTTP 接口统一返回对象。
 *
 * <p>所有业务服务对外提供接口时，统一使用该对象包装返回结果，
 * 避免不同服务返回不同格式，方便前端统一处理。</p>
 *
 * @param <T> 返回数据的类型
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
     * 创建统一返回对象。
     *
     * @param code    业务状态码
     * @param message 返回提示信息
     * @param data    实际业务数据
     */
    private ApiResponse(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 创建成功响应。
     *
     * @param data 成功返回的数据
     * @param <T>  数据类型
     * @return 成功响应对象
     */
    public static <T> ApiResponse<T> success(T data) {
        // 使用统一的成功状态码，避免在业务代码中直接写魔法值。
        return new ApiResponse<>(
                CommonErrorCode.SUCCESS.code(),
                CommonErrorCode.SUCCESS.message(),
                data
        );
    }

    /**
     * 创建失败响应。
     *
     * @param errorCode 统一错误码
     * @param <T>       返回数据类型
     * @return 失败响应对象
     */
    public static <T> ApiResponse<T> failure(ErrorCode errorCode) {
        // 错误码不能为空，否则无法构造有效的错误响应。
        ErrorCode validErrorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");

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
     * @param errorCode 统一错误码
     * @param message   自定义提示信息
     * @param <T>       返回数据类型
     * @return 失败响应对象
     */
    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String message) {
        // 错误码不能为空，否则无法确定错误类型。
        ErrorCode validErrorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");

        // 如果调用方没有提供提示信息，则使用错误码中的默认提示。
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
     * 获取实际业务数据。
     *
     * @return 业务数据
     */
    public T getData() {
        return data;
    }
}
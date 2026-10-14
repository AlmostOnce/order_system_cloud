package com.hbue.ordering.common.core.exception;

import com.hbue.ordering.common.core.error.ErrorCode;

import java.util.Objects;

/**
 * 可预期业务失败的统一运行时异常。
 *
 * <p>异常携带 {@link ErrorCode}，由 Web 异常处理边界转换为统一响应；
 * 它用于表达业务规则拒绝，不用于隐藏数据库、网络等基础设施故障。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
public class BusinessException extends RuntimeException {

    /** 用于统一响应映射的业务错误码。 */
    private final ErrorCode errorCode;

    /**
     * 使用错误码默认提示创建业务异常。
     *
     * @param errorCode 业务错误码，不能为空
     * @throws NullPointerException 当错误码为 null 时抛出
     */
    public BusinessException(ErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode).message());
        this.errorCode = errorCode;
    }

    /**
     * 使用指定提示信息创建业务异常。
     *
     * @param errorCode 业务错误码，不能为空
     * @param message 面向调用方的业务失败说明
     * @throws NullPointerException 当错误码为 null 时抛出
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode);
    }

    /**
     * 获取异常携带的业务错误码。
     *
     * @return 用于生成统一错误响应的错误码
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

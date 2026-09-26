package com.hbue.ordering.common.core.error;

public enum CommonErrorCode implements ErrorCode {

    BAD_REQUEST("COMMON_400", "请求参数错误"),
    UNAUTHORIZED("COMMON_401", "未登录或登录已过期"),
    FORBIDDEN("COMMON_403", "没有访问权限"),
    NOT_FOUND("COMMON_404", "资源不存在"),
    CONFLICT("COMMON_409", "请求冲突"),
    TOO_MANY_REQUESTS("COMMON_429", "请求过于频繁，请稍后再试"),
    INTERNAL_ERROR("COMMON_500", "系统内部异常"),
    SERVICE_UNAVAILABLE("COMMON_503", "服务暂不可用，请稍后重试"),
    SUCCESS("COMMON_000", "操作成功");

    private final String code;
    private final String message;

    CommonErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}

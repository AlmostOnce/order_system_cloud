package com.hbue.ordering.common.core.error;

/**
 * 项目通用错误码枚举。
 *
 * <p>集中定义可跨业务服务复用的请求、认证、权限和系统级错误标识，
 * 供统一响应与异常映射使用；具体业务模块的专属错误码应由对应模块定义。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
public enum CommonErrorCode implements ErrorCode {

    /** 请求参数缺失、格式错误或不符合接口约束。 */
    BAD_REQUEST("COMMON_400", "请求参数错误"),
    /** 请求未通过身份认证，或登录凭据已失效。 */
    UNAUTHORIZED("COMMON_401", "未登录或登录已过期"),
    /** 请求者身份有效，但没有执行当前操作的权限。 */
    FORBIDDEN("COMMON_403", "没有访问权限"),
    /** 请求目标不存在，或按隐私策略不向请求者暴露其存在性。 */
    NOT_FOUND("COMMON_404", "资源不存在"),
    /** 请求与当前资源状态冲突，无法按当前状态继续处理。 */
    CONFLICT("COMMON_409", "请求冲突"),
    /** 请求触发频率限制，需要等待后重试。 */
    TOO_MANY_REQUESTS("COMMON_429", "请求过于频繁，请稍后再试"),
    /** 未分类的服务端内部错误。 */
    INTERNAL_ERROR("COMMON_500", "系统内部异常"),
    /** 服务或其依赖暂不可用，调用方可稍后重试。 */
    SERVICE_UNAVAILABLE("COMMON_503", "服务暂不可用，请稍后重试"),
    /** 业务操作成功；响应体仍可携带实际业务数据。 */
    SUCCESS("COMMON_000", "操作成功");

    /** 对外返回且跨版本保持稳定的错误码。 */
    private final String code;

    /** 错误码对应的默认中文提示。 */
    private final String message;

    /**
     * 创建通用错误码。
     *
     * @param code 对外返回的稳定错误标识
     * @param message 默认中文提示信息
     */
    CommonErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 获取对外返回的稳定错误标识。
     *
     * @return 错误码字符串
     */
    @Override
    public String code() {
        return code;
    }

    /**
     * 获取该错误码对应的默认中文提示。
     *
     * @return 默认提示信息
     */
    @Override
    public String message() {
        return message;
    }
}

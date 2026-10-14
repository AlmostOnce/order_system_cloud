package com.hbue.ordering.common.core.error;

/**
 * 统一错误码契约。
 *
 * <p>通用模块和各业务模块的错误码均通过该接口暴露稳定编码及默认提示，
 * 使异常处理和响应封装不必依赖某一个具体枚举。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
public interface ErrorCode {

    /**
     * 获取可供接口调用方识别的错误码。
     *
     * @return 稳定的错误码字符串
     */
    String code();

    /**
     * 获取错误码对应的默认中文提示。
     *
     * @return 默认提示信息
     */
    String message();
}

package com.hbue.ordering.stock.api.error;

import com.hbue.ordering.common.core.error.ErrorCode;

/**
 * 库存服务业务错误码。
 *
 * <p>定义在契约模块中，供 stock-service 抛出与 order-service 识别共用：
 * 下单链路必须能区分「库存不足」与「服务不可用」，前者提示用户换菜或减量，
 * 后者应提示稍后重试。</p>
 *
 * @author order-system
 * @date 2026-10-19
 */
public enum StockErrorCode implements ErrorCode {

    /**
     * 可售数量不足，扣减被拒绝。
     */
    STOCK_NOT_ENOUGH("STOCK_409_01", "库存不足，请调整购买数量"),

    /**
     * 指定档口菜品没有库存记录。
     */
    STOCK_NOT_FOUND("STOCK_404_01", "库存记录不存在"),

    /**
     * 并发冲突导致乐观锁扣减多次失败。
     */
    STOCK_CONFLICT("STOCK_409_02", "库存并发冲突，请重试");

    /**
     * 稳定的错误码字符串。
     */
    private final String code;

    /**
     * 默认中文提示。
     */
    private final String message;

    /**
     * 创建库存业务错误码。
     *
     * @param code 错误码字符串
     * @param message 默认提示
     */
    StockErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 获取错误码字符串。
     *
     * @return 错误码
     */
    @Override
    public String code() {
        return code;
    }

    /**
     * 获取默认提示。
     *
     * @return 默认中文提示
     */
    @Override
    public String message() {
        return message;
    }
}

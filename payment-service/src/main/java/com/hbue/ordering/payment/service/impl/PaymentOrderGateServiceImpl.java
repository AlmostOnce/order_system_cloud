package com.hbue.ordering.payment.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.payment.mapper.OrderPaymentGateMapper;
import com.hbue.ordering.payment.service.PaymentOrderGateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 订单支付入口闸门的实现。
 *
 * <p>使用 {@link TransactionTemplate} 显式开启事务而不是注解，原因是被包裹的业务动作由调用方
 * 以 lambda 传入，注解式事务无法覆盖到 lambda 内部；显式模板能保证"补行 → 加锁 → 判断 → 动作"
 * 全部处于同一个事务和同一把行锁之下。</p>
 *
 * <p>{@code ensure} 先补出闸门行再 {@code lock} 加行锁，避免两个并发请求都因"行不存在"而各插入
 * 一次的竞态；已被关闭时抛冲突异常让支付回调失败，由上游按幂等规则重试或人工核对。</p>
 *
 * @author order-system
 * @date 2026-10-30
 */
@Service
public class PaymentOrderGateServiceImpl implements PaymentOrderGateService {

    /**
     * 闸门状态数据访问。
     */
    private final OrderPaymentGateMapper mapper;

    /**
     * 显式事务模板，用于把调用方传入的动作纳入事务范围。
     */
    private final TransactionTemplate transactions;

    /**
     * 创建闸门服务。
     *
     * @param mapper  闸门状态数据访问
     * @param manager 平台事务管理器
     */
    public PaymentOrderGateServiceImpl(OrderPaymentGateMapper mapper, org.springframework.transaction.PlatformTransactionManager manager) {
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(manager);
    }

    /**
     * 在确认支付入口开放的前提下执行动作。
     */
    @Override
    public <T> T whileOpen(Long id, Supplier<T> action) {
        return transactions.execute(status -> {
            mapper.ensure(id);
            if (!Integer.valueOf(0).equals(mapper.lock(id)))
                throw new BusinessException(CommonErrorCode.CONFLICT, "订单支付入口已关闭");
            return action.get();
        });
    }

    /**
     * 关闭支付入口，阻止后续支付回调继续记账。
     */
    @Override
    public void beginClose(Long id) {
        transactions.executeWithoutResult(status -> {
            mapper.ensure(id);
            mapper.lock(id);
            mapper.beginClose(id);
        });
    }

    /**
     * 撤销关闭意图，恢复支付入口。
     *
     * <p>与 {@link #beginClose(Long)} 使用同一份"补行 → 行锁 → 更新"顺序，避免撤销与并发关单互相覆盖；
     * 不在事务内做任何远程调用，失败时整体回滚，不会留下半关闭状态。</p>
     */
    @Override
    public void reopen(Long id) {
        transactions.executeWithoutResult(status -> {
            mapper.ensure(id);
            mapper.lock(id);
            mapper.reopen(id);
        });
    }
}

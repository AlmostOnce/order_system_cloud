package com.hbue.ordering.auth.service;

/**
 * 登录失败保护服务契约。
 *
 * <p>按账号维护连续登录失败计数和临时锁定状态，使多个认证服务实例共享相同的
 * 防暴力尝试状态。调用方只传入用户名，不接触 Redis Key 的生成和存储细节。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
public interface LoginAttemptService {

    /**
     * 检查账号当前是否处于临时锁定状态。
     *
     * @param username 登录用户名
     * @return 账号是否仍在锁定期内
     * @throws com.hbue.ordering.common.core.exception.BusinessException
     *         Redis 不可用或无法确认锁定状态时抛出
     */
    boolean isLocked(String username);

    /**
     * 记录一次用户名或密码校验失败。
     *
     * @param username 登录用户名
     * @return 本次失败是否已达到阈值并触发临时锁定
     * @throws com.hbue.ordering.common.core.exception.BusinessException
     *         Redis 不可用或无法确认计数结果时抛出
     */
    boolean recordFailure(String username);

    /**
     * 清除账号此前累计的失败次数和锁定标记。
     *
     * @param username 登录用户名
     * @throws com.hbue.ordering.common.core.exception.BusinessException Redis 不可用时抛出
     */
    void recordSuccess(String username);
}

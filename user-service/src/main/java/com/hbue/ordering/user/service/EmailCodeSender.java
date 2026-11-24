package com.hbue.ordering.user.service;

/**
 * 邮件验证码发送边界。
 */
public interface EmailCodeSender {
    default void sendPayPasswordReset(String email, String code) {
        send(email, code);
    }

    default void sendOwnershipVerification(String email, String code) {
        send(email, code);
    }

    /**
     * 发送账号注销验证码。
     *
     * <p>验证码只用于证明用户仍能访问当前绑定邮箱，不与其他用途共用。</p>
     *
     * @param email 绑定邮箱
     * @param code  6 位验证码
     */
    default void sendAccountDeletion(String email, String code) {
        send(email, code);
    }

    /**
     * 发送账号注销状态通知。
     *
     * <p>通知正文由服务层生成，只包含注销时间、撤回方式和阻断原因等业务信息，
     * 不得包含验证码、令牌或密码。发送失败不应回滚已经完成的注销状态变更。</p>
     *
     * @param email   接收邮箱
     * @param subject 邮件主题
     * @param text    纯文本正文
     */
    void sendNotice(String email, String subject, String text);

    void send(String email, String code);

    default void sendPasswordReset(String email, String code) {
        send(email, code);
    }
}

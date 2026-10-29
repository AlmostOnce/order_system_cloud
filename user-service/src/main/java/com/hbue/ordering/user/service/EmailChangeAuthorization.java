package com.hbue.ordering.user.service;

/** 当前用户邮箱变更前的短时密码验证凭据。 */
public interface EmailChangeAuthorization {
    String authorize(Long userId, String loginPassword);
    void verify(Long userId, String authorizationToken);
    void consume(Long userId, String authorizationToken);
}

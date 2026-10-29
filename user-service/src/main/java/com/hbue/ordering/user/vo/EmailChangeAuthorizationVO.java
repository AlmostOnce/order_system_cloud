package com.hbue.ordering.user.vo;

/** 登录密码验证成功后签发的短时邮箱变更授权凭据。 */
public record EmailChangeAuthorizationVO(String authorizationToken, long expiresInSeconds) { }

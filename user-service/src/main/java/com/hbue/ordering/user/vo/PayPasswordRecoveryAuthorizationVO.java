package com.hbue.ordering.user.vo;
public record PayPasswordRecoveryAuthorizationVO(String authorizationToken, String maskedEmail) {
    @Override public String toString() { return "PayPasswordRecoveryAuthorizationVO[REDACTED]"; }
}

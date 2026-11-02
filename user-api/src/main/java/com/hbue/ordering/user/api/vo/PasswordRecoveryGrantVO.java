package com.hbue.ordering.user.api.vo;

public record PasswordRecoveryGrantVO(String resetToken, int expiresIn) {
    @Override public String toString() { return "PasswordRecoveryGrantVO[redacted]"; }
}

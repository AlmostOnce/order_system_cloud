package com.hbue.ordering.user.api.dto;

public record PasswordRecoveryVerifyCommand(String email, String code) {
    @Override public String toString() { return "PasswordRecoveryVerifyCommand[redacted]"; }
}

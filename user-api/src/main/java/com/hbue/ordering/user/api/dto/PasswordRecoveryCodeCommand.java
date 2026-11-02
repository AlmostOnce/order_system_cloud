package com.hbue.ordering.user.api.dto;

public record PasswordRecoveryCodeCommand(String email) {
    @Override public String toString() { return "PasswordRecoveryCodeCommand[redacted]"; }
}

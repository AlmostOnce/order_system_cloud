package com.hbue.ordering.user.api.dto;

public record PasswordRecoveryConfirmCommand(String resetToken, String newPassword) {
    @Override public String toString() { return "PasswordRecoveryConfirmCommand[redacted]"; }
}

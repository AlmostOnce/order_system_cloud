package com.hbue.ordering.user.api.dto;
public record RegistrationEmailVerifyCommand(String email, String code) {
    @Override public String toString() {return "RegistrationEmailVerifyCommand[REDACTED]";}
}

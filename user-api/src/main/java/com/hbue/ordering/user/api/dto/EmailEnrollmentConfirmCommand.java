package com.hbue.ordering.user.api.dto;
public record EmailEnrollmentConfirmCommand(String authorizationToken, String email, String code) {
    @Override public String toString() {return "EmailEnrollmentConfirmCommand[REDACTED]";}
}

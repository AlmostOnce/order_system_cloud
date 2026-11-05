package com.hbue.ordering.user.api.dto;
public record EmailEnrollmentSendCommand(String authorizationToken, String email) {
    @Override public String toString() {return "EmailEnrollmentSendCommand[REDACTED]";}
}

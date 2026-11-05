package com.hbue.ordering.user.api.vo;
public record EmailRegistrationGrantVO(String verificationToken,long expiresIn) {
    @Override public String toString(){return "EmailRegistrationGrantVO[REDACTED]";}
}

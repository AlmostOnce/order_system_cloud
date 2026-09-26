package com.hbue.ordering.common.core.exception;

import com.hbue.ordering.common.core.error.ErrorCode;

import java.util.Objects;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode).message());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
package com.logistics.api.common;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final int code;
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.errorCode = errorCode;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.errorCode = null;
    }

    public static BusinessException of(ErrorCode errorCode) {
        return new BusinessException(errorCode);
    }

    public static BusinessException of(ErrorCode errorCode, String message) {
        return new BusinessException(errorCode, message);
    }

    public static BusinessException of(int code, String message) {
        return new BusinessException(code, message);
    }
}

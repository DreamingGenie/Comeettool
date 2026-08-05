package com.ssafy.backend.global.exception;

import lombok.Getter;

/**
 * 도메인 공통 예외. ErrorCode로 상태·코드·메시지를 전달한다.
 */
@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object data;

    public CustomException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public CustomException(ErrorCode errorCode, Object data) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.data = data;
    }
}

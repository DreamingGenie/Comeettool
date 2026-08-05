package com.ssafy.backend.global.exception;

import lombok.Getter;

/**
 * 도메인 공통 예외. ErrorCode로 상태·코드·메시지를 전달한다.
 */
@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}

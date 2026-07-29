package com.ssafy.backend.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 공통 에러 코드 (에러 카탈로그). 인증 규약(auth-jwt-contract §4)의 코드 포함.
 */
@Getter
public enum ErrorCode {

    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_EXPIRED", "액세스 토큰이 만료되었습니다."),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID", "유효하지 않은 토큰입니다."),
    AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "인증이 필요합니다."),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", "권한이 없습니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "입력값이 올바르지 않습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 오류가 발생했습니다."),

    AUTH_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "AUTH_EMAIL_DUPLICATED", "이미 사용 중인 이메일입니다."),
    AUTH_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_LOGIN_FAILED", "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTH_REFRESH_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_FAILED", "리프레시 토큰이 유효하지 않습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),

    SPACE_NOT_FOUND(HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "스페이스를 찾을 수 없습니다."),
    SPACE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "SPACE_ACCESS_DENIED", "해당 스페이스에 접근할 권한이 없습니다."),
    SPACE_OWNER_CANNOT_LEAVE(HttpStatus.CONFLICT, "SPACE_OWNER_CANNOT_LEAVE",
            "소유자는 스페이스를 나갈 수 없습니다. 소유권을 위임하거나 스페이스를 삭제해 주세요."),

    PASSWORD_MISMATCH(HttpStatus.UNAUTHORIZED, "PASSWORD_MISMATCH", "현재 비밀번호가 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}

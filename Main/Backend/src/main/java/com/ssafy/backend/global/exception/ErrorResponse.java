package com.ssafy.backend.global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.util.List;

/**
 * 공통 에러 응답 바디 (README §0): { code, message, data?, errors[] }.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final String code;
    private final String message;
    private final Object data;
    private final List<FieldError> errors;

    private ErrorResponse(String code, String message, Object data, List<FieldError> errors) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.errors = errors;
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), null, null);
    }

    public static ErrorResponse of(ErrorCode errorCode, Object data) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), data, null);
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> errors) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), null, errors);
    }

    @Getter
    public static class FieldError {
        private final String field;
        private final String reason;

        public FieldError(String field, String reason) {
            this.field = field;
            this.reason = reason;
        }
    }
}

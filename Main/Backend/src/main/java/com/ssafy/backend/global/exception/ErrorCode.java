package com.ssafy.backend.global.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

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
    SPACE_OWNER_LAST_MEMBER(HttpStatus.CONFLICT, "SPACE_OWNER_LAST_MEMBER",
            "소유자가 혼자인 스페이스는 나갈 수 없습니다. 스페이스를 삭제해 주세요."),
    SPACE_OWNER_MUST_TRANSFER(HttpStatus.CONFLICT, "SPACE_OWNER_MUST_TRANSFER",
            "소유자는 나가기 전에 다른 멤버에게 소유권을 위임해야 합니다."),
    SPACE_OWNER_ONLY(HttpStatus.FORBIDDEN, "SPACE_OWNER_ONLY", "소유자만 수행할 수 있는 작업입니다."),
    SPACE_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "SPACE_MEMBER_NOT_FOUND", "해당 스페이스의 멤버를 찾을 수 없습니다."),
    SPACE_ALREADY_OWNER(HttpStatus.CONFLICT, "SPACE_ALREADY_OWNER", "이미 스페이스 소유자입니다."),
    SPACE_TRANSFER_TARGET_NOT_ELIGIBLE(HttpStatus.BAD_REQUEST, "SPACE_TRANSFER_TARGET_NOT_ELIGIBLE",
            "게스트에게는 소유권을 위임할 수 없습니다."),

    MEMBER_ALREADY_JOINED(HttpStatus.CONFLICT, "MEMBER_ALREADY_JOINED", "이미 스페이스의 멤버입니다."),
    INVITATION_ALREADY_PENDING(HttpStatus.CONFLICT, "INVITATION_ALREADY_PENDING", "이미 대기 중인 초대가 있습니다."),
    // 만료/미존재/본인 초대가 아닌 경우를 모두 동일 코드로 응답 — 타인의 초대 존재 여부가 노출되지 않도록 404로 통합.
    INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND", "유효한 초대를 찾을 수 없습니다."),
    MEMBER_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "MEMBER_LIMIT_EXCEEDED", "스페이스 멤버는 최대 10명까지 참여할 수 있습니다."),

    MEETING_NOT_FOUND(HttpStatus.NOT_FOUND, "MEETING_NOT_FOUND", "회의를 찾을 수 없습니다."),
    MEETING_ACCESS_DENIED(HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED",
            "해당 회의에 접근할 권한이 없습니다."),
    MEETING_HOST_REQUIRED(HttpStatus.FORBIDDEN, "MEETING_HOST_REQUIRED", "회의 호스트 권한이 필요합니다."),
    MEETING_PARTICIPANT_NOT_FOUND(HttpStatus.NOT_FOUND, "MEETING_PARTICIPANT_NOT_FOUND",
            "회의 참여자를 찾을 수 없습니다."),
    MEETING_HOST_ALREADY_ASSIGNED(HttpStatus.CONFLICT, "MEETING_HOST_ALREADY_ASSIGNED",
            "이미 해당 회의의 호스트입니다."),
    MEETING_HOST_CANNOT_LEAVE(HttpStatus.CONFLICT, "MEETING_HOST_CANNOT_LEAVE",
            "호스트는 권한을 양도하거나 회의를 종료해야 합니다."),
    MEETING_CREATE_FORBIDDEN(HttpStatus.FORBIDDEN, "MEETING_CREATE_FORBIDDEN",
            "게스트는 회의를 생성할 수 없습니다."),
    MEETING_ROOM_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "MEETING_ROOM_LIMIT_EXCEEDED",
            "팀 스페이스에서 동시에 진행할 수 있는 회의는 최대 3개입니다."),
    MEETING_LIVEKIT_DISCONNECT_FAILED(HttpStatus.BAD_GATEWAY, "MEETING_LIVEKIT_DISCONNECT_FAILED",
            "회의 연결 종료 처리에 실패했습니다."),
    MEETING_LIVEKIT_WEBHOOK_UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "MEETING_LIVEKIT_WEBHOOK_UNAUTHORIZED",
            "유효하지 않은 LiveKit 웹훅 요청입니다."
    ),
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "문서를 찾을 수 없습니다."),
    DOCUMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "DOCUMENT_ACCESS_DENIED", "해당 문서에 접근할 권한이 없습니다."),
    DOCUMENT_CREATE_FORBIDDEN(HttpStatus.FORBIDDEN, "DOCUMENT_CREATE_FORBIDDEN", "문서를 생성할 권한이 없습니다."),
    DOCUMENT_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, "DOCUMENT_DELETE_FORBIDDEN", "문서를 삭제할 권한이 없습니다."),

    PASSWORD_MISMATCH(HttpStatus.UNAUTHORIZED, "PASSWORD_MISMATCH", "현재 비밀번호가 올바르지 않습니다."),
    ALREADY_DELETED_USER(HttpStatus.CONFLICT, "ALREADY_DELETED_USER", "이미 탈퇴한 회원입니다."),

    PROFILE_IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "PROFILE_IMAGE_TOO_LARGE", "프로필 사진은 5MB 이하만 업로드할 수 있습니다."),
    PROFILE_IMAGE_INVALID_TYPE(HttpStatus.BAD_REQUEST, "PROFILE_IMAGE_INVALID_TYPE", "jpg, jpeg, png 형식만 업로드할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}

package com.ssafy.backend.member.dto;

import jakarta.validation.constraints.NotNull;

/**
 * MEMBER-07 멤버 권한 변경 요청.
 * MEMBER·GUEST만 허용한다. OWNER는 Bean Validation이 아니라 서비스 계층에서 명시적으로 거부해
 * 위임 API 안내가 담긴 메시지를 별도로 내려준다.
 */
public record RequestChangeAuthorityDto(
        @NotNull(message = "변경할 권한은 필수입니다.")
        String authority
) {
}
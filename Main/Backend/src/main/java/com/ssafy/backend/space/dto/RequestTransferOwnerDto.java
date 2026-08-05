package com.ssafy.backend.space.dto;

import jakarta.validation.constraints.NotNull;

/**
 * SPACE-101 소유권 위임 요청.
 * 새 소유자는 해당 스페이스의 멤버(users.user_id)로 지정한다.
 */
public record RequestTransferOwnerDto(
        @NotNull(message = "새 소유자 ID는 필수입니다.")
        Long newOwnerUserId
) {
}

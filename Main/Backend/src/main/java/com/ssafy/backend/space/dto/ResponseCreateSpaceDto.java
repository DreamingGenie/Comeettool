package com.ssafy.backend.space.dto;

import java.time.OffsetDateTime;

/**
 * SPACE-01 스페이스 생성 응답. 생성된 스페이스 요약과 소유자 식별자를 담는다.
 */
public record ResponseCreateSpaceDto(
        Long spaceId,
        String teamName,
        String teamDescription,
        String teamColor,
        String teamProfileImage,
        Long ownerId,
        OffsetDateTime createdAt
) {
}

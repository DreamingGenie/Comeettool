package com.ssafy.backend.space.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * SPACE-05 스페이스 상세. 스페이스 정보 + 참여자(멤버) 목록.
 */
public record ResponseSpaceDetailDto(
        Long spaceId,
        String teamName,
        String teamDescription,
        String teamColor,
        String teamProfileImage,
        Long ownerId,
        OffsetDateTime createdAt,
        List<ResponseSpaceMemberDto> members
) {
}

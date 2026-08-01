package com.ssafy.backend.space.dto;

/**
 * SPACE-08 스페이스 정보 수정 응답. 수정 후 스페이스 요약을 담는다.
 */
public record ResponseUpdateSpaceDto(
        Long spaceId,
        String teamName,
        String teamDescription,
        String teamColor,
        String teamProfileImage,
        Long ownerId
) {
}

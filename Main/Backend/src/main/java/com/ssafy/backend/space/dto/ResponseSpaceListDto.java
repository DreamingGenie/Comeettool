package com.ssafy.backend.space.dto;

/**
 * SPACE-02 참여 중 스페이스 목록의 개별 항목.
 * myRole = 로그인 사용자의 해당 스페이스 역할, memberCount = 참여자 수.
 */
public record ResponseSpaceListDto(
        Long spaceId,
        String teamName,
        String teamDescription,
        String teamColor,
        String teamProfileImage,
        Long ownerId,
        String myRole,
        long memberCount
) {
}

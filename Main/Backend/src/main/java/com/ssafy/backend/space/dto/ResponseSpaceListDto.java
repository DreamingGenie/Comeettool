package com.ssafy.backend.space.dto;

/**
 * SPACE-02 참여 중 스페이스 목록의 개별 항목.
 * myAuthority = 로그인 사용자의 해당 스페이스 권한(OWNER/MEMBER/GUEST), memberCount = 참여자 수.
 */
public record ResponseSpaceListDto(
        Long spaceId,
        String teamName,
        String teamDescription,
        String teamColor,
        String teamProfileImage,
        Long ownerId,
        String myAuthority,
        long memberCount
) {
}

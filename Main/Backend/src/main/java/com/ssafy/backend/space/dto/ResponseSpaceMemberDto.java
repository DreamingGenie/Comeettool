package com.ssafy.backend.space.dto;

/**
 * SPACE-05 스페이스 상세의 참여자 항목.
 * teamRoleId = 배정된 역할 식별자(미배정 시 null). 역할명 노출은 MEMBER-06 범위에서 확장.
 */
public record ResponseSpaceMemberDto(
        Long memberId,
        Long userId,
        String nickname,
        String authority,
        Long teamRoleId
) {
}

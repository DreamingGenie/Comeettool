package com.ssafy.backend.space.dto;

/**
 * SPACE-05 스페이스 상세의 참여자 항목.
 */
public record ResponseSpaceMemberDto(
        Long memberId,
        Long userId,
        String nickname,
        String role,
        String authority
) {
}

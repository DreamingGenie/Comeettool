package com.ssafy.backend.member.dto;

/**
 * MEMBER-07 멤버 권한 변경 응답.
 */
public record ResponseChangeAuthorityDto(
        Long memberId,
        String authority
) {
}
package com.ssafy.backend.space.dto;

// SPACE 팀 프로필 이미지 변경 성공 응답 — 대상 스페이스 ID와 새 이미지 접근 URL.
public record ResponseSpaceProfileImageDto(
        Long spaceId,
        String teamProfileImage
) {
}

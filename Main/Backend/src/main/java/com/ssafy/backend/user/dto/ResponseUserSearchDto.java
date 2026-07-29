package com.ssafy.backend.user.dto;

public record ResponseUserSearchDto(
        Long userId,
        String nickname,
        String email,
        String profileImage
) {
}
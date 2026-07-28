package com.ssafy.backend.user.dto;

public record ResponseMyProfileDto(
        Long userId,
        String email,
        String nickname,
        String phone,
        String profileImage,
        String sex,
        Integer age,
        String jobFamily,
        String jobRole,
        String userDescription,
        String userColor
) {
}
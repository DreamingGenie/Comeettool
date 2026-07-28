package com.ssafy.backend.user.dto;

// AUTH-07: 비밀번호 변경 성공 응답 — 재발급된 토큰을 포함한다.
public record ResponseChangePasswordDto(
        String tokenType,
        String accessToken,
        String refreshToken
) {
}
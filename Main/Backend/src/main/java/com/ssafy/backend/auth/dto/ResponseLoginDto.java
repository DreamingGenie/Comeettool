package com.ssafy.backend.auth.dto;

public record ResponseLoginDto(
        String tokenType,
        String accessToken,
        String refreshToken,
        String userId
) {
}
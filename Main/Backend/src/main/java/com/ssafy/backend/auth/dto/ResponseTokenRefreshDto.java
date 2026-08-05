package com.ssafy.backend.auth.dto;

public record ResponseTokenRefreshDto(
        String tokenType,
        String accessToken
) {
}
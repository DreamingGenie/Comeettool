package com.ssafy.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RequestTokenRefreshDto(

        @NotBlank
        String refreshToken
) {
}
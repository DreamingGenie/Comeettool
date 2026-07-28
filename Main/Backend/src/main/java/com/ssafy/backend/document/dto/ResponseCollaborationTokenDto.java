package com.ssafy.backend.document.dto;

import java.time.OffsetDateTime;

public record ResponseCollaborationTokenDto(
        String token,
        OffsetDateTime expiresAt,
        String permission
) {
}

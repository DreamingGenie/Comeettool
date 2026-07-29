package com.ssafy.backend.document.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ResponseDocumentDetailDto(
        UUID documentId,
        Long teamId,
        String title,
        int finalVersion,
        int stateEpoch,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

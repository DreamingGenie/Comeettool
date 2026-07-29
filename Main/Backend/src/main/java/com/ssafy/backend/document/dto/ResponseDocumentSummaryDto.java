package com.ssafy.backend.document.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ResponseDocumentSummaryDto(
        UUID documentId,
        Long teamId,
        String title,
        int finalVersion,
        OffsetDateTime updatedAt
) {
}

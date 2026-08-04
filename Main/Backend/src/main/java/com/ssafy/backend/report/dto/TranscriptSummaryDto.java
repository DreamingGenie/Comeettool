package com.ssafy.backend.report.dto;

import java.time.OffsetDateTime;

/**
 * REPORTS-01 전사 목록 조회 응답 항목.
 */
public record TranscriptSummaryDto(
        Long meetingId,
        String meetingRoomName,
        OffsetDateTime createdAt
) {
}

package com.ssafy.backend.report.dto;

import java.time.OffsetDateTime;

/**
 * REPORTS-04 회의록 목록 조회 응답 항목.
 */
public record MinutesSummaryDto(
        Long meetingId,
        String meetingRoomName,
        String title,
        Boolean isConfirmed,
        OffsetDateTime createdAt
) {
}

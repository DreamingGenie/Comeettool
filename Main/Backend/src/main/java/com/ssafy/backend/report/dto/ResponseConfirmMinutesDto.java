package com.ssafy.backend.report.dto;

import java.time.OffsetDateTime;

/**
 * REPORTS-07 회의록 확정 응답.
 */
public record ResponseConfirmMinutesDto(
        Long meetingId,
        Boolean isConfirmed,
        OffsetDateTime confirmedAt
) {
}

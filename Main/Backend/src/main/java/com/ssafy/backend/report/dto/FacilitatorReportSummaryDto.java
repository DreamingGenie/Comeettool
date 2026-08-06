package com.ssafy.backend.report.dto;

import java.time.OffsetDateTime;

/**
 * REPORTS-09 퍼실리테이터 리포트 목록 조회 응답 항목.
 * meetingType은 백엔드가 기본값을 채우지 않고 null이면 null 그대로 응답한다(프론트 재량).
 */
public record FacilitatorReportSummaryDto(
        Long meetingId,
        String meetingRoomName,
        String title,
        String meetingType,
        OffsetDateTime createdAt
) {
}

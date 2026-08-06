package com.ssafy.backend.report.dto;

/**
 * REPORTS-03 리포트 내보내기 공통 응답. REPORTS-06/07/10/11에서도 동일 구조로 재사용될 예정이다.
 */
public record ResponseExportDto(
        Long meetingId,
        String format,
        String url
) {
}

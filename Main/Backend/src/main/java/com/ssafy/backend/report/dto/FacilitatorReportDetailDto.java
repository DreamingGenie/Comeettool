package com.ssafy.backend.report.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonRawValue;

/**
 * REPORTS-10 퍼실리테이터 리포트 상세 조회 응답.
 * participationStats/qualityEvaluation/strengths/improvements/decisionProcessChecks/
 * unresolvedIssuesEvaluation/nextMeetingSuggestions는 FacilitatorReport의 JSONB 원문(raw JSON 문자열)을
 * 그대로 담는다. {@link JsonRawValue}로 이미 JSON인 문자열을 그대로 끼워 넣어, 다시 문자열로 이스케이프되어
 * 이중 직렬화되는 것을 막는다 — MinutesDetailDto(REPORTS-05)/TranscriptDetailDto(REPORTS-02)와 동일한 패턴이며,
 * Main Backend는 이 필드들의 내부 구조를 파싱/검증하지 않는다.
 */
public record FacilitatorReportDetailDto(
        Long meetingId,
        String title,
        String meetingType,
        String overallReview,
        String participationComment,
        @JsonRawValue String participationStats,
        @JsonRawValue String qualityEvaluation,
        @JsonRawValue String strengths,
        @JsonRawValue String improvements,
        @JsonRawValue String decisionProcessChecks,
        @JsonRawValue String unresolvedIssuesEvaluation,
        @JsonRawValue String nextMeetingSuggestions,
        OffsetDateTime createdAt
) {
}

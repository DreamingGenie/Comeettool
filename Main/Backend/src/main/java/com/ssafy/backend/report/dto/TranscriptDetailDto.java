package com.ssafy.backend.report.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonRawValue;

/**
 * REPORTS-02 전사 상세 조회 응답.
 * transcript는 AudioTranscription.transcript(JSONB → raw JSON 문자열)를 그대로 담는다.
 * {@link JsonRawValue}로 이미 JSON인 문자열을 그대로 끼워 넣어, 다시 문자열로 이스케이프되어
 * 이중 직렬화(예: "\"[{...}]\"")되는 것을 막는다 — Main Backend는 세그먼트 구조를 파싱/검증하지 않는다.
 */
public record TranscriptDetailDto(
        Long meetingId,
        @JsonRawValue String transcript,
        OffsetDateTime createdAt
) {
}

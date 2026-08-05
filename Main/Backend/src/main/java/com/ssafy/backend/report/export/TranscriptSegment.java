package com.ssafy.backend.report.export;

/**
 * AudioTranscription.transcript(JSONB raw) 배열의 개별 원소 파싱용 DTO.
 * 예: {"speaker":"ssong123","start":12.5,"end":15.8,"text":"..."}
 */
record TranscriptSegment(
        String speaker,
        Double start,
        Double end,
        String text
) {
}

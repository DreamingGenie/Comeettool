package com.ssafy.backend.report.export;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * REPORTS-03: AudioTranscription.transcript(JSONB raw, 화자별 발화 세그먼트 배열)를
 * 시간순으로 정렬한 md 문서로 렌더링한다.
 * 문서 조립(제목/섹션 구조화) 자체는 범용 {@link MarkdownRenderer}에 위임하고,
 * 이 클래스는 STT 세그먼트 파싱과 줄 포맷팅만 담당한다 — 다른 리포트 타입은 이 클래스를 참고해
 * 각자의 원본 데이터를 {@link MarkdownSection} 목록으로 변환하는 렌더러를 별도로 구현하면 된다.
 */
@Component
@RequiredArgsConstructor
public class TranscriptMarkdownRenderer {

    private final ObjectMapper objectMapper;

    public String render(Long meetingId, String transcriptJson) {
        List<TranscriptSegment> segments = parse(transcriptJson);
        List<String> items = segments.stream()
                .sorted(Comparator.comparingDouble(TranscriptMarkdownRenderer::startOf))
                .map(TranscriptMarkdownRenderer::toLine)
                .toList();

        return MarkdownRenderer.render(
                "회의 전사 (Meeting #" + meetingId + ")",
                List.of(new MarkdownSection(null, items)));
    }

    private List<TranscriptSegment> parse(String transcriptJson) {
        try {
            return objectMapper.readValue(transcriptJson, new TypeReference<List<TranscriptSegment>>() {
            });
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private static double startOf(TranscriptSegment segment) {
        return segment.start() == null ? 0.0 : segment.start();
    }

    private static String toLine(TranscriptSegment segment) {
        return "[" + formatTimestamp(startOf(segment)) + "] " + segment.speaker() + ": " + segment.text();
    }

    private static String formatTimestamp(double seconds) {
        long total = Math.round(seconds);
        return "%02d:%02d".formatted(total / 60, total % 60);
    }
}

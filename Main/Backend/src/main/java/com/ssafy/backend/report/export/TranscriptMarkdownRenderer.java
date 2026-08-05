package com.ssafy.backend.report.export;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * REPORTS-03/REPORTS-08 md 렌더링 유틸.
 * AudioTranscription.transcript(JSONB raw, 화자별 발화 세그먼트 배열)와
 * MeetingMinutes(title/summary/topics/decisions/actionItems/openIssues, 각 JSONB raw)를
 * md 문서로 렌더링한다. 문서 조립(제목/섹션 구조화) 자체는 범용 {@link MarkdownRenderer}에 위임하고,
 * 이 클래스는 리포트 타입별 JSON 파싱과 줄 포맷팅만 담당한다(REPORTS-08에서 두 리포트 타입의 렌더러를
 * 별도 클래스로 나누지 않고 이 클래스에 함께 두기로 함).
 *
 * <p>topics/decisions/actionItems/openIssues는 AI_BE가 채우는 필드라 Main Backend가 스키마를
 * 강제하지 않는다 — 실제 운영 데이터에서 topics가 {@code {"title":..,"summary":..}}, openIssues가
 * 객체가 아닌 순수 문자열 배열로 오는 등 REPORTS-05/06 문서 예시와 다른 모양이 실제로 관측됐다
 * (엄격한 타입(record)으로 파싱하다가 500이 났던 사례). 그래서 고정 스키마의 record 대신
 * {@link JsonNode} 트리를 순회하며 몇 가지 흔한 키 이름을 느슨하게 찾는 방식으로 렌더링한다 —
 * 어떤 모양이 와도 500을 내지 않고 최대한 읽을 수 있는 한 줄을 만드는 것이 목표다.</p>
 */
@Component
@RequiredArgsConstructor
public class TranscriptMarkdownRenderer {

    // item이 JSON 객체일 때 "제목"에 해당하는 값을 찾는 우선순위 키.
    private static final List<String> PRIMARY_KEYS =
            List.of("title", "topic", "decision", "task", "issue", "summary", "text", "description", "content");

    // item이 JSON 객체일 때 제목 옆 괄호에 덧붙일 부가 정보 키(제목으로 이미 쓰인 값은 중복 표기하지 않는다).
    private static final List<String> DETAIL_KEYS = List.of("summary", "owner", "assignee", "dueDate", "status");

    private final ObjectMapper objectMapper;

    public String render(Long meetingId, String transcriptJson) {
        List<TranscriptSegment> segments = parseList(transcriptJson, new TypeReference<List<TranscriptSegment>>() {
        });
        List<String> items = segments.stream()
                .sorted(Comparator.comparingDouble(TranscriptMarkdownRenderer::startOf))
                .map(TranscriptMarkdownRenderer::toLine)
                .toList();

        return MarkdownRenderer.render(
                "회의 전사 (Meeting #" + meetingId + ")",
                List.of(new MarkdownSection(null, items)));
    }

    public String renderMinutes(
            String title, String summary, String topicsJson, String decisionsJson,
            String actionItemsJson, String openIssuesJson) {
        List<MarkdownSection> sections = new ArrayList<>();
        sections.add(new MarkdownSection("요약", List.of(summary)));
        sections.add(new MarkdownSection("안건", itemLines(topicsJson)));
        sections.add(new MarkdownSection("결정사항", itemLines(decisionsJson)));
        sections.add(new MarkdownSection("액션 아이템", itemLines(actionItemsJson)));
        sections.add(new MarkdownSection("미해결 이슈", itemLines(openIssuesJson)));

        return MarkdownRenderer.render(title, sections);
    }

    private List<String> itemLines(String arrayJson) {
        JsonNode array = readTree(arrayJson);
        List<String> lines = new ArrayList<>();
        for (JsonNode item : array) {
            lines.add(itemLine(item));
        }
        return lines;
    }

    private static String itemLine(JsonNode item) {
        if (item.isTextual()) {
            return item.asText();
        }
        if (!item.isObject()) {
            return item.toString();
        }

        String primary = firstNonBlankText(item, PRIMARY_KEYS);
        List<String> details = new ArrayList<>();
        for (String key : DETAIL_KEYS) {
            String value = textOrNull(item.get(key));
            if (value != null && !value.equals(primary)) {
                details.add(value);
            }
        }

        if (primary == null) {
            // 알려진 키가 하나도 없으면 객체의 문자열 값을 전부 이어붙인다 — 완전히 못 읽는 것보다는 낫다.
            return fallbackJoin(item);
        }
        return details.isEmpty() ? primary : primary + " (" + String.join(", ", details) + ")";
    }

    private static String firstNonBlankText(JsonNode item, List<String> keys) {
        for (String key : keys) {
            String value = textOrNull(item.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        String text = node.asText();
        return text.isBlank() ? null : text;
    }

    private static String fallbackJoin(JsonNode item) {
        List<String> values = new ArrayList<>();
        Iterator<JsonNode> fields = item.elements();
        while (fields.hasNext()) {
            JsonNode value = fields.next();
            if (value.isTextual() && !value.asText().isBlank()) {
                values.add(value.asText());
            }
        }
        return values.isEmpty() ? item.toString() : String.join(", ", values);
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private <T> List<T> parseList(String json, TypeReference<List<T>> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
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

package com.ssafy.backend.report.export;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * REPORTS-03/REPORTS-08/REPORTS-11 md 렌더링 유틸.
 * AudioTranscription.transcript(JSONB raw, 화자별 발화 세그먼트 배열),
 * MeetingMinutes(title/summary/topics/decisions/actionItems/openIssues, 각 JSONB raw),
 * FacilitatorReport(title/meetingType/overallReview/participationComment + 7개 JSONB raw)를
 * md 문서로 렌더링한다. 문서 조립(제목/섹션 구조화) 자체는 범용 {@link MarkdownRenderer}에 위임하고,
 * 이 클래스는 리포트 타입별 JSON 파싱과 줄 포맷팅만 담당한다(REPORTS-08/11에서 리포트 타입마다
 * 렌더러 클래스를 나누지 않고 이 클래스에 함께 두기로 함).
 *
 * <p>topics/decisions/actionItems/openIssues, participationStats/qualityEvaluation/strengths/
 * improvements/decisionProcessChecks/unresolvedIssuesEvaluation/nextMeetingSuggestions는 전부
 * AI_BE가 채우는 필드라 Main Backend가 스키마를 강제하지 않는다 — 실제 운영 데이터에서 topics가
 * {@code {"title":..,"summary":..}}, openIssues가 객체가 아닌 순수 문자열 배열로 오는 등 REPORTS-05/06
 * 문서 예시와 다른 모양이 실제로 관측됐다(엄격한 타입(record)으로 파싱하다가 500이 났던 사례).
 * 그래서 고정 스키마의 record 대신 {@link JsonNode} 트리를 순회하며 몇 가지 흔한 키 이름을 느슨하게
 * 찾는 방식으로 렌더링한다 — 어떤 모양이 와도 500을 내지 않고 최대한 읽을 수 있는 한 줄을 만드는 것이
 * 목표다. {@code itemLines}(배열용)/{@code objectFieldLines}(단일 객체용) 두 헬퍼를 REPORTS-06/08/11의
 * 모든 리포트 타입이 공유한다.</p>
 */
@Component
@RequiredArgsConstructor
public class TranscriptMarkdownRenderer {

    // item이 JSON 객체일 때 "제목"에 해당하는 값을 찾는 우선순위 키.
    // 리포트 타입마다 후보 키 목록을 따로 두지 않고 하나로 합쳐서 재사용한다 — 목록을 여러 개
    // 유지하는 것보다, 후보를 넉넉히 잡아둔 목록 하나를 모든 리포트 타입이 같이 쓰는 편이
    // (알려지지 않은 리포트별 실제 키 이름에 대한) 방어력이 더 높고 코드 중복도 없다.
    // AI_BE 소스(app/pipeline/{minutes,facilitator}/schema.py) 확인 결과 실제 필드명은 snake_case이고
    // (예: Decision={content,timestamp}, Improvement={issue,timestamp,suggestion}), 초기에 가정했던
    // 이름(예: decision, topic)과 다른 경우가 있어 실제 확인된 키를 추가하되 기존 가정도 안전하게 남겨둔다
    // (틀린 후보는 그냥 안 걸릴 뿐 해가 되지 않는다).
    private static final List<String> PRIMARY_KEYS = List.of(
            "title", "topic", "decision", "task", "issue", "point", "check", "suggestion", "speaker",
            "content", "grade", "summary", "text", "description");

    // item이 JSON 객체일 때 제목 옆 괄호에 덧붙일 부가 정보 키(제목으로 이미 쓰인 값은 중복 표기하지 않는다).
    private static final List<String> DETAIL_KEYS = List.of(
            "summary", "owner", "assignee", "due_date", "dueDate", "status", "timestamp",
            "source_timestamp", "evidence_timestamp", "consensus_type", "suggestion",
            "ratio", "speaking_seconds", "utterance_count", "talkTimeRatio", "score", "rating");

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

    public String renderFacilitatorReport(
            String title, String meetingType, String overallReview, String participationComment,
            String participationStatsJson, String qualityEvaluationJson, String strengthsJson,
            String improvementsJson, String decisionProcessChecksJson, String unresolvedIssuesEvaluationJson,
            String nextMeetingSuggestionsJson) {
        List<MarkdownSection> sections = new ArrayList<>();
        if (meetingType != null && !meetingType.isBlank()) {
            sections.add(new MarkdownSection("회의 유형", List.of(meetingType)));
        }
        sections.add(new MarkdownSection("총평", List.of(overallReview)));
        sections.add(new MarkdownSection("참여 코멘트", List.of(participationComment)));
        sections.add(new MarkdownSection("참여 통계", itemLines(participationStatsJson)));
        // qualityEvaluation은 다른 6개 필드와 달리 배열이 아니라 단일 객체라 itemLines가 아닌
        // key-value 나열용 objectFieldLines로 렌더링한다(고정 스키마 가정 없이 그대로 나열).
        sections.add(new MarkdownSection("품질 평가", objectFieldLines(qualityEvaluationJson)));
        sections.add(new MarkdownSection("강점", itemLines(strengthsJson)));
        sections.add(new MarkdownSection("개선점", itemLines(improvementsJson)));
        sections.add(new MarkdownSection("의사결정 프로세스 점검", itemLines(decisionProcessChecksJson)));
        sections.add(new MarkdownSection("미해결 이슈 평가", itemLines(unresolvedIssuesEvaluationJson)));
        sections.add(new MarkdownSection("다음 회의 제안", itemLines(nextMeetingSuggestionsJson)));

        return MarkdownRenderer.render(title, sections);
    }

    /** JSON 배열을 한 줄씩 렌더링한다(topics/decisions/.../strengths/improvements/... 전부 이 형태). */
    private List<String> itemLines(String arrayJson) {
        JsonNode array = readTree(arrayJson);
        List<String> lines = new ArrayList<>();
        for (JsonNode item : array) {
            lines.add(itemLine(item));
        }
        return lines;
    }

    /** JSON 단일 객체를 "key: value" 줄 목록으로 렌더링한다(qualityEvaluation 전용). */
    private List<String> objectFieldLines(String objectJson) {
        JsonNode node = readTree(objectJson);
        if (!node.isObject()) {
            // 객체가 아니면(배열 등) 배열 렌더링 경로로 방어적으로 처리한다.
            List<String> lines = new ArrayList<>();
            node.forEach(item -> lines.add(itemLine(item)));
            return lines;
        }

        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            JsonNode value = field.getValue();
            String scalar = scalarText(value);
            if (scalar != null) {
                lines.add(field.getKey() + ": " + scalar);
            } else if (value.isObject()) {
                // 예: quality_evaluation.agenda_clarity = {"grade":"우수","evidence_timestamp":"..."}
                // 값 자체가 중첩 객체인 경우 — itemLine의 제목/부가정보 매칭을 그대로 재사용한다.
                lines.add(field.getKey() + ": " + itemLine(value));
            } else if (value.isArray() && !value.isEmpty()) {
                List<String> nested = new ArrayList<>();
                value.forEach(item -> nested.add(itemLine(item)));
                lines.add(field.getKey() + ": " + String.join("; ", nested));
            }
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

        String primary = firstMatch(item, PRIMARY_KEYS);
        List<String> details = new ArrayList<>();
        for (String key : DETAIL_KEYS) {
            String value = scalarText(item.get(key));
            if (value != null && !value.equals(primary)) {
                details.add(value);
            }
        }

        if (primary == null) {
            // 알려진 키가 하나도 없으면 객체의 스칼라 값을 전부 이어붙인다 — 완전히 못 읽는 것보다는 낫다.
            return fallbackJoin(item);
        }
        return details.isEmpty() ? primary : primary + " (" + String.join(", ", details) + ")";
    }

    private static String firstMatch(JsonNode item, List<String> keys) {
        for (String key : keys) {
            String value = scalarText(item.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /** node가 문자열/숫자/불린 같은 스칼라 값이면 텍스트로, null/누락/컨테이너(배열·객체)면 null을 반환한다. */
    private static String scalarText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || node.isContainerNode()) {
            return null;
        }
        String text = node.asText();
        return text.isBlank() ? null : text;
    }

    private static String fallbackJoin(JsonNode item) {
        List<String> values = new ArrayList<>();
        Iterator<JsonNode> elements = item.elements();
        while (elements.hasNext()) {
            String value = scalarText(elements.next());
            if (value != null) {
                values.add(value);
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

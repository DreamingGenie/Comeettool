package com.ssafy.backend.report.dto;

import tools.jackson.databind.JsonNode;

/**
 * REPORTS-06 회의록 부분 수정 요청. 모든 필드가 선택(optional)이며, null이거나 필드 자체를 보내지 않으면
 * 해당 값은 변경하지 않는다(MEMBER-16 RequestUpdateTeamRoleDto와 동일한 부분 수정 컨벤션).
 * topics/decisions/actionItems/openIssues는 AI_BE가 채우는 JSONB 원문 구조를 그대로 받아 검증 없이 저장하는
 * pass-through 필드라 String이 아닌 {@link JsonNode}로 받는다 — 요청 바디에서 실제 JSON 배열/객체로 오기 때문에
 * (예: "topics": [...]) String 타입으로는 Jackson이 바인딩할 수 없다. 배열 전체 교체만 지원하며
 * (항목 단위 부분 수정 없음), 빈 배열([])은 "전체 삭제"를 의미하는 유효한 값으로 취급한다.
 * 이 프로젝트(Spring Boot 4/Spring 7)의 HTTP 메시지 컨버터는 Jackson 3({@code tools.jackson.*})을 쓰므로,
 * 반드시 {@code tools.jackson.databind.JsonNode}를 써야 한다 — Jackson 2({@code com.fasterxml.jackson.databind.JsonNode})는
 * Spring이 인식하지 못해 바인딩 시 500(InvalidDefinitionException)이 난다.
 */
public record RequestUpdateMinutesDto(
        String title,
        String summary,
        JsonNode topics,
        JsonNode decisions,
        JsonNode actionItems,
        JsonNode openIssues
) {
}

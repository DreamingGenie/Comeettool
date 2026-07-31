package com.ssafy.backend.member.dto;

import java.time.OffsetDateTime;

/**
 * Redis invitation:{invitationId} 키에 JSON으로 저장되는 초대 데이터.
 * 상태 필드 없음 — 존재 여부 자체가 "대기 중"을 의미하며, 만료는 TTL(1일)에 위임한다.
 */
public record InvitationData(
        Long spaceId,
        Long inviterId,
        Long targetUserId,
        OffsetDateTime createdAt
) {
}
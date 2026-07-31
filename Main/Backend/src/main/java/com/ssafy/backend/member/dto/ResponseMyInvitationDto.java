package com.ssafy.backend.member.dto;

import java.time.OffsetDateTime;

/**
 * 내가 받은 초대 목록 조회 응답 항목.
 */
public record ResponseMyInvitationDto(
        String invitationId,
        Long spaceId,
        String spaceName,
        String inviterNickname,
        OffsetDateTime createdAt
) {
}
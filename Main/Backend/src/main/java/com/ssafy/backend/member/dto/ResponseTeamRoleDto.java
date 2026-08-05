package com.ssafy.backend.member.dto;

import java.time.OffsetDateTime;

/**
 * MEMBER-14 팀 역할 생성 응답.
 */
public record ResponseTeamRoleDto(
        Long teamRoleId,
        String roleName,
        String color,
        OffsetDateTime createdAt
) {
}
package com.ssafy.backend.member.dto;

/**
 * MEMBER-15 팀 역할 목록 조회 응답 항목.
 */
public record ResponseTeamRoleSummaryDto(
        Long teamRoleId,
        String roleName,
        String color
) {
}
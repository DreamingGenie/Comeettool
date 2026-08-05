package com.ssafy.backend.member.dto;

/**
 * MEMBER-06 멤버 팀 역할 배정/해제 요청.
 * teamRoleId가 없거나 null이면 역할 해제로 처리한다.
 */
public record RequestAssignTeamRoleDto(
        Long teamRoleId
) {
}
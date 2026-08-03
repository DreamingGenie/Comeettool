package com.ssafy.backend.member.dto;

/**
 * MEMBER-06 멤버 팀 역할 배정/해제 응답. 해제 시 teamRoleId·roleName은 null이다.
 */
public record ResponseAssignTeamRoleDto(
        Long memberId,
        Long teamRoleId,
        String roleName
) {
}
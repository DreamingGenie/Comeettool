package com.ssafy.backend.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * MEMBER-14 팀 역할 생성 요청.
 */
public record RequestCreateTeamRoleDto(
        @NotBlank(message = "역할 이름은 필수입니다.")
        @Size(min = 1, max = 20, message = "역할 이름은 1~20자여야 합니다.")
        String roleName,
        String color
) {
}
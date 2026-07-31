package com.ssafy.backend.member.dto;

import jakarta.validation.constraints.NotNull;

/**
 * MEMBER-02 멤버 초대 요청.
 */
public record RequestInviteMemberDto(
        @NotNull(message = "초대 대상 사용자 ID는 필수입니다.")
        Long targetUserId
) {
}
package com.ssafy.backend.meeting.dto;

import jakarta.validation.constraints.NotNull;

/**
 * MEET-06 호스트 양도 요청.
 * 새 호스트는 해당 회의에 등록된 Participant 식별자로 지정한다.
 */
public record RequestTransferHostDto(
        @NotNull(message = "새 호스트 ID는 필수입니다.")
        Long nextHostParticipantId
) {
}

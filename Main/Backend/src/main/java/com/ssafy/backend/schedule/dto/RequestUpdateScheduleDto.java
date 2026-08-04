package com.ssafy.backend.schedule.dto;

import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * SCHEDULE-04 일정 수정 요청(부분 수정).
 * 모든 필드는 선택 — 전달된(null이 아닌) 필드만 갱신한다.
 * userIdArr을 전달하면 참여자 목록 전체를 교체한다.
 */
public record RequestUpdateScheduleDto(

        @Size(max = 1000)
        String category,

        @Size(max = 1000)
        String title,

        String description,

        OffsetDateTime startTime,

        OffsetDateTime endTime,

        List<Long> userIdArr,

        @Size(max = 20)
        String color
) {
}

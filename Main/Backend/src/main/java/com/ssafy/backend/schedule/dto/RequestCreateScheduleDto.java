package com.ssafy.backend.schedule.dto;

import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * SCHEDULE-03 일정 생성 요청.
 * 제목·설명·시간·참여자는 선택(스키마상 NULL 허용). 참여자 미지정 시 생성자를 기본 포함한다(Service).
 * 종일(all-day) 개념은 다루지 않으며 start/end 시각만 저장한다.
 */
public record RequestCreateScheduleDto(

        @Size(max = 1000)
        String category,

        @Size(max = 1000)
        String title,

        String description,

        OffsetDateTime startTime,

        OffsetDateTime endTime,

        // 참여자 user_id 목록. 미지정(null/빈 배열) 시 생성자만 포함한다.
        List<Long> userIdArr
) {
}

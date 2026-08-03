package com.ssafy.backend.schedule.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 일정 응답(단건·목록 공용). SCHEDULE-01/02/03/04에서 사용한다.
 */
public record ResponseScheduleDto(
        Long scheduleId,
        Long teamId,
        Long creatorId,
        String category,
        String title,
        String description,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        List<Long> userIdArr
) {
}

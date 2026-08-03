package com.ssafy.backend.schedule.mapper;

import com.ssafy.backend.schedule.dto.ResponseScheduleDto;
import com.ssafy.backend.schedule.entity.Schedule;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * schedule 도메인 Entity ↔ Dto 변환 전담.
 */
@Component
public class ScheduleMapper {

    public ResponseScheduleDto toResponse(Schedule schedule) {
        return new ResponseScheduleDto(
                schedule.getId(),
                schedule.getTeamId(),
                schedule.getCreatorId(),
                schedule.getCategory(),
                schedule.getTitle(),
                schedule.getDescription(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getUserIdArr()
        );
    }

    public List<ResponseScheduleDto> toResponseList(List<Schedule> schedules) {
        return schedules.stream()
                .map(this::toResponse)
                .toList();
    }
}

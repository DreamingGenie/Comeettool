package com.ssafy.backend.schedule.service;

import com.ssafy.backend.schedule.dto.RequestCreateScheduleDto;
import com.ssafy.backend.schedule.dto.RequestUpdateScheduleDto;
import com.ssafy.backend.schedule.dto.ResponseScheduleDto;

import java.util.List;

public interface ScheduleService {

    // SCHEDULE-01: 내가 참여자로 포함된 모든 일정.
    List<ResponseScheduleDto> findMySchedules(Long userId);

    // SCHEDULE-02: 스페이스(팀) 일정 목록.
    List<ResponseScheduleDto> findScheduleList(Long userId, Long spaceId);

    // SCHEDULE-03: 일정 생성.
    ResponseScheduleDto addSchedule(Long userId, Long spaceId, RequestCreateScheduleDto request);

    // SCHEDULE-04: 일정 부분 수정.
    ResponseScheduleDto modifySchedule(Long userId, Long scheduleId, RequestUpdateScheduleDto request);

    // SCHEDULE-05: 일정 삭제(soft delete).
    void removeSchedule(Long userId, Long scheduleId);
}

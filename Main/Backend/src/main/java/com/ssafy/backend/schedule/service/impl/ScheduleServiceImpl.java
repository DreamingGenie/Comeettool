package com.ssafy.backend.schedule.service.impl;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.member.repository.MemberRepository;
import com.ssafy.backend.schedule.dto.RequestCreateScheduleDto;
import com.ssafy.backend.schedule.dto.RequestUpdateScheduleDto;
import com.ssafy.backend.schedule.dto.ResponseScheduleDto;
import com.ssafy.backend.schedule.entity.Schedule;
import com.ssafy.backend.schedule.mapper.ScheduleMapper;
import com.ssafy.backend.schedule.repository.ScheduleRepository;
import com.ssafy.backend.schedule.service.ScheduleService;
import com.ssafy.backend.space.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 일정(SCHEDULE-01~05) 비즈니스 로직.
 * 조회 권한 = 스페이스 멤버, 수정·삭제 권한 = 생성자 또는 스페이스 Owner.
 */
@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final MemberRepository memberRepository;
    private final TeamRepository teamRepository;
    private final ScheduleMapper scheduleMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ResponseScheduleDto> findMySchedules(Long userId) {
        return scheduleMapper.toResponseList(scheduleRepository.findMySchedules(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResponseScheduleDto> findScheduleList(Long userId, Long spaceId) {
        validateMember(spaceId, userId);
        return scheduleMapper.toResponseList(
                scheduleRepository.findByTeamIdAndIsDeletedFalseOrderByStartTimeAsc(spaceId));
    }

    @Override
    @Transactional
    public ResponseScheduleDto addSchedule(Long userId, Long spaceId, RequestCreateScheduleDto request) {
        validateMember(spaceId, userId);
        validateTimeRange(request.startTime(), request.endTime());

        Schedule schedule = Schedule.builder()
                .teamId(spaceId)
                .creatorId(userId)
                .category(request.category())
                .title(request.title())
                .description(request.description())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .userIdArr(resolveParticipants(userId, request.userIdArr()))
                .build();

        return scheduleMapper.toResponse(scheduleRepository.save(schedule));
    }

    @Override
    @Transactional
    public ResponseScheduleDto modifySchedule(Long userId, Long scheduleId, RequestUpdateScheduleDto request) {
        Schedule schedule = findActiveSchedule(scheduleId);
        validateModifiable(schedule, userId);
        validateTimeRange(request.startTime(), request.endTime());

        schedule.updateInfo(
                request.category(),
                request.title(),
                request.description(),
                request.startTime(),
                request.endTime(),
                request.userIdArr());

        return scheduleMapper.toResponse(schedule);
    }

    @Override
    @Transactional
    public void removeSchedule(Long userId, Long scheduleId) {
        Schedule schedule = findActiveSchedule(scheduleId);
        validateModifiable(schedule, userId);
        schedule.softDelete(OffsetDateTime.now());
    }

    private Schedule findActiveSchedule(Long scheduleId) {
        return scheduleRepository.findByIdAndIsDeletedFalse(scheduleId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    // 조회 권한: 해당 스페이스 멤버만.
    private void validateMember(Long spaceId, Long userId) {
        if (!memberRepository.existsByTeamIdAndUserId(spaceId, userId)) {
            throw new CustomException(ErrorCode.SPACE_ACCESS_DENIED);
        }
    }

    // 수정·삭제 권한: 생성자 또는 스페이스 Owner.
    private void validateModifiable(Schedule schedule, Long userId) {
        if (schedule.isCreator(userId) || isTeamOwner(schedule.getTeamId(), userId)) {
            return;
        }
        throw new CustomException(ErrorCode.SCHEDULE_ACCESS_DENIED);
    }

    private boolean isTeamOwner(Long teamId, Long userId) {
        return teamRepository.findByIdAndIsDeletedFalse(teamId)
                .map(team -> team.getOwnerId().equals(userId))
                .orElse(false);
    }

    // 참여자 미지정(null/빈 배열) 시 생성자만 포함한다.
    private List<Long> resolveParticipants(Long creatorId, List<Long> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of(creatorId);
        }
        return requested;
    }

    // start·end가 모두 있을 때만 순서를 검증한다(둘 다 선택 필드).
    private void validateTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new CustomException(ErrorCode.VALIDATION_FAILED);
        }
    }
}

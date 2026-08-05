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
import com.ssafy.backend.space.entity.Team;
import com.ssafy.backend.space.repository.TeamRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ScheduleServiceImpl 단위 테스트. Repository는 Mock, Mapper는 실제 구현을 Spy로 사용 — DB 없이 로직만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleServiceImpl 단위 테스트")
class ScheduleServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long SPACE_ID = 10L;
    private static final Long SCHEDULE_ID = 100L;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private TeamRepository teamRepository;

    @Spy
    private ScheduleMapper scheduleMapper = new ScheduleMapper();

    @InjectMocks
    private ScheduleServiceImpl scheduleService;

    private Schedule schedule(Long creatorId, List<Long> userIdArr) {
        Schedule s = Schedule.builder()
                .teamId(SPACE_ID)
                .creatorId(creatorId)
                .title("일정")
                .userIdArr(userIdArr)
                .build();
        ReflectionTestUtils.setField(s, "id", SCHEDULE_ID);
        return s;
    }

    private Team teamOwnedBy(Long ownerId) {
        return Team.builder().name("팀").ownerId(ownerId).color("#123456").build();
    }

    @Nested
    @DisplayName("SCHEDULE-01 내 일정 조회")
    class FindMySchedules {

        @Test
        @DisplayName("리포지토리의 내 일정 결과를 응답 DTO로 매핑해 반환한다")
        void mapsRepositoryResult() {
            given(scheduleRepository.findMySchedules(USER_ID))
                    .willReturn(List.of(schedule(USER_ID, List.of(USER_ID))));

            List<ResponseScheduleDto> result = scheduleService.findMySchedules(USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).creatorId()).isEqualTo(USER_ID);
        }
    }

    @Nested
    @DisplayName("SCHEDULE-02 팀 일정 목록")
    class FindScheduleList {

        @Test
        @DisplayName("스페이스 멤버가 아니면 SPACE_ACCESS_DENIED")
        void deniesNonMember() {
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, USER_ID)).willReturn(false);

            assertThatThrownBy(() -> scheduleService.findScheduleList(USER_ID, SPACE_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.SPACE_ACCESS_DENIED);
        }

        @Test
        @DisplayName("멤버면 팀 일정 목록을 반환한다")
        void returnsListForMember() {
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, USER_ID)).willReturn(true);
            given(scheduleRepository.findByTeamIdAndIsDeletedFalseOrderByStartTimeAsc(SPACE_ID))
                    .willReturn(List.of(schedule(USER_ID, List.of(USER_ID))));

            assertThat(scheduleService.findScheduleList(USER_ID, SPACE_ID)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("SCHEDULE-03 일정 생성")
    class AddSchedule {

        private RequestCreateScheduleDto request(List<Long> userIdArr, OffsetDateTime start, OffsetDateTime end) {
            return new RequestCreateScheduleDto("회의", "제목", "설명", start, end, userIdArr, null);
        }

        @Test
        @DisplayName("스페이스 멤버가 아니면 생성하지 못한다")
        void deniesNonMember() {
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, USER_ID)).willReturn(false);

            assertThatThrownBy(() -> scheduleService.addSchedule(USER_ID, SPACE_ID, request(null, null, null)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.SPACE_ACCESS_DENIED);
            verify(scheduleRepository, never()).save(any());
        }

        @Test
        @DisplayName("참여자 미지정 시 생성자만 참여자로 포함한다")
        void defaultsParticipantToCreator() {
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, USER_ID)).willReturn(true);
            given(scheduleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            scheduleService.addSchedule(USER_ID, SPACE_ID, request(null, null, null));

            ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
            verify(scheduleRepository).save(captor.capture());
            assertThat(captor.getValue().getUserIdArr()).containsExactly(USER_ID);
            assertThat(captor.getValue().getCreatorId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("참여자를 지정하면 그대로 저장한다")
        void keepsProvidedParticipants() {
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, USER_ID)).willReturn(true);
            given(scheduleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            scheduleService.addSchedule(USER_ID, SPACE_ID, request(List.of(USER_ID, OTHER_ID), null, null));

            ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
            verify(scheduleRepository).save(captor.capture());
            assertThat(captor.getValue().getUserIdArr()).containsExactly(USER_ID, OTHER_ID);
        }

        @Test
        @DisplayName("색상 미지정 시 기본색(#566FEA)으로 저장한다")
        void defaultsColor() {
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, USER_ID)).willReturn(true);
            given(scheduleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            scheduleService.addSchedule(USER_ID, SPACE_ID, request(null, null, null));

            ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
            verify(scheduleRepository).save(captor.capture());
            assertThat(captor.getValue().getColor()).isEqualTo("#566FEA");
        }

        @Test
        @DisplayName("색상을 지정하면 그대로 저장한다")
        void keepsProvidedColor() {
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, USER_ID)).willReturn(true);
            given(scheduleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            RequestCreateScheduleDto req =
                    new RequestCreateScheduleDto("회의", "제목", "설명", null, null, null, "#FF0000");
            scheduleService.addSchedule(USER_ID, SPACE_ID, req);

            ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
            verify(scheduleRepository).save(captor.capture());
            assertThat(captor.getValue().getColor()).isEqualTo("#FF0000");
        }

        @Test
        @DisplayName("시작이 종료보다 늦으면 VALIDATION_FAILED")
        void rejectsInvalidTimeRange() {
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, USER_ID)).willReturn(true);
            OffsetDateTime start = OffsetDateTime.parse("2026-08-10T11:00:00+09:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-08-10T10:00:00+09:00");

            assertThatThrownBy(() -> scheduleService.addSchedule(USER_ID, SPACE_ID, request(null, start, end)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
            verify(scheduleRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("SCHEDULE-04 일정 수정")
    class ModifySchedule {

        private RequestUpdateScheduleDto titleUpdate() {
            return new RequestUpdateScheduleDto(null, "수정된 제목", null, null, null, null, null);
        }

        @Test
        @DisplayName("존재하지 않으면 SCHEDULE_NOT_FOUND")
        void notFound() {
            given(scheduleRepository.findByIdAndIsDeletedFalse(SCHEDULE_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> scheduleService.modifySchedule(USER_ID, SCHEDULE_ID, titleUpdate()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.SCHEDULE_NOT_FOUND);
        }

        @Test
        @DisplayName("생성자는 수정할 수 있다")
        void creatorCanModify() {
            given(scheduleRepository.findByIdAndIsDeletedFalse(SCHEDULE_ID))
                    .willReturn(Optional.of(schedule(USER_ID, List.of(USER_ID))));

            ResponseScheduleDto result = scheduleService.modifySchedule(USER_ID, SCHEDULE_ID, titleUpdate());

            assertThat(result.title()).isEqualTo("수정된 제목");
        }

        @Test
        @DisplayName("생성자가 아니어도 스페이스 Owner면 수정할 수 있다")
        void ownerCanModify() {
            given(scheduleRepository.findByIdAndIsDeletedFalse(SCHEDULE_ID))
                    .willReturn(Optional.of(schedule(OTHER_ID, List.of(OTHER_ID))));
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID))
                    .willReturn(Optional.of(teamOwnedBy(USER_ID)));

            ResponseScheduleDto result = scheduleService.modifySchedule(USER_ID, SCHEDULE_ID, titleUpdate());

            assertThat(result.title()).isEqualTo("수정된 제목");
        }

        @Test
        @DisplayName("생성자도 Owner도 아니면 SCHEDULE_ACCESS_DENIED")
        void deniesOthers() {
            given(scheduleRepository.findByIdAndIsDeletedFalse(SCHEDULE_ID))
                    .willReturn(Optional.of(schedule(OTHER_ID, List.of(OTHER_ID))));
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID))
                    .willReturn(Optional.of(teamOwnedBy(OTHER_ID)));

            assertThatThrownBy(() -> scheduleService.modifySchedule(USER_ID, SCHEDULE_ID, titleUpdate()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.SCHEDULE_ACCESS_DENIED);
        }
    }

    @Nested
    @DisplayName("SCHEDULE-05 일정 삭제")
    class RemoveSchedule {

        @Test
        @DisplayName("생성자는 soft delete 할 수 있다")
        void creatorCanDelete() {
            Schedule target = schedule(USER_ID, List.of(USER_ID));
            given(scheduleRepository.findByIdAndIsDeletedFalse(SCHEDULE_ID)).willReturn(Optional.of(target));

            scheduleService.removeSchedule(USER_ID, SCHEDULE_ID);

            assertThat(target.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("권한 없는 사용자는 삭제하지 못한다")
        void deniesOthers() {
            Schedule target = schedule(OTHER_ID, List.of(OTHER_ID));
            given(scheduleRepository.findByIdAndIsDeletedFalse(SCHEDULE_ID)).willReturn(Optional.of(target));
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID))
                    .willReturn(Optional.of(teamOwnedBy(OTHER_ID)));

            assertThatThrownBy(() -> scheduleService.removeSchedule(USER_ID, SCHEDULE_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.SCHEDULE_ACCESS_DENIED);
            assertThat(target.isDeleted()).isFalse();
        }
    }
}

package com.ssafy.backend.report.service.impl;

import java.lang.reflect.Constructor;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.ssafy.backend.global.common.PageResponse;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.meeting.entity.MeetingRoom;
import com.ssafy.backend.meeting.repository.MeetingRoomRepository;
import com.ssafy.backend.member.repository.MemberRepository;
import com.ssafy.backend.report.dto.TranscriptDetailDto;
import com.ssafy.backend.report.dto.TranscriptSummaryDto;
import com.ssafy.backend.report.entity.AudioTranscription;
import com.ssafy.backend.report.repository.AudioTranscriptionRepository;
import com.ssafy.backend.space.entity.Team;
import com.ssafy.backend.space.repository.TeamRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * ReportServiceImpl 단위 테스트. Repository는 전부 Mock — 비즈니스 로직만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportServiceImpl 단위 테스트")
class ReportServiceImplTest {

    private static final Long OWNER_ID = 1L;
    private static final Long SPACE_ID = 10L;
    private static final Long MEETING_ID = 34L;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MeetingRoomRepository meetingRoomRepository;

    @Mock
    private AudioTranscriptionRepository audioTranscriptionRepository;

    @InjectMocks
    private ReportServiceImpl reportService;

    private Team activeTeam(Long spaceId, Long ownerId) {
        Team team = Team.builder().name("팀A").description("설명").ownerId(ownerId).color("#123456").build();
        ReflectionTestUtils.setField(team, "id", spaceId);
        return team;
    }

    private MeetingRoom meetingRoomOf(Long meetingId, Long teamId) {
        MeetingRoom meetingRoom = MeetingRoom.builder().teamId(teamId).hostId(OWNER_ID).name("스프린트 회의").build();
        ReflectionTestUtils.setField(meetingRoom, "id", meetingId);
        return meetingRoom;
    }

    // AudioTranscription은 순수 조회 전용 엔티티라 빌더/공개 생성자를 두지 않는다 —
    // 테스트에서는 protected 기본 생성자를 리플렉션으로 열어 ReflectionTestUtils로 필드를 채운다.
    private AudioTranscription transcriptionOf(Long meetingId, String transcript, OffsetDateTime createdAt) {
        try {
            Constructor<AudioTranscription> constructor = AudioTranscription.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            AudioTranscription entity = constructor.newInstance();
            ReflectionTestUtils.setField(entity, "meetingId", meetingId);
            ReflectionTestUtils.setField(entity, "transcript", transcript);
            ReflectionTestUtils.setField(entity, "createdAt", createdAt);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("REPORTS-01 전사 목록 조회")
    class GetTranscripts {

        @Test
        @DisplayName("정상 조회 시 Page 결과가 PageResponse 필드로 정확히 매핑된다")
        void getTranscripts_returnsMappedPageResponse() {
            Team team = activeTeam(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);

            List<TranscriptSummaryDto> content = List.of(
                    new TranscriptSummaryDto(34L, "8월 4주차 스프린트 회의", OffsetDateTime.parse("2026-08-04T05:00:00Z")),
                    new TranscriptSummaryDto(33L, "7월 회고", OffsetDateTime.parse("2026-07-28T05:00:00Z")));
            Pageable pageable = PageRequest.of(0, 10);
            Page<TranscriptSummaryDto> page = new PageImpl<>(content, pageable, 23);
            given(audioTranscriptionRepository.findAllByTeamId(SPACE_ID, pageable)).willReturn(page);

            PageResponse<TranscriptSummaryDto> result = reportService.getTranscripts(OWNER_ID, SPACE_ID, pageable);

            assertThat(result.content()).hasSize(2);
            assertThat(result.content().get(0).meetingId()).isEqualTo(34L);
            assertThat(result.content().get(0).meetingRoomName()).isEqualTo("8월 4주차 스프린트 회의");
            assertThat(result.page()).isEqualTo(0);
            assertThat(result.size()).isEqualTo(10);
            assertThat(result.totalElements()).isEqualTo(23);
            assertThat(result.totalPages()).isEqualTo(3);
            assertThat(result.hasNext()).isTrue();
        }

        @Test
        @DisplayName("결과가 없으면 빈 content를 반환한다(예외 아님)")
        void getTranscripts_returnsEmptyContentWhenNoneExist() {
            Team team = activeTeam(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);

            Pageable pageable = PageRequest.of(0, 10);
            Page<TranscriptSummaryDto> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            given(audioTranscriptionRepository.findAllByTeamId(SPACE_ID, pageable)).willReturn(emptyPage);

            PageResponse<TranscriptSummaryDto> result = reportService.getTranscripts(OWNER_ID, SPACE_ID, pageable);

            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isEqualTo(0);
            assertThat(result.totalPages()).isEqualTo(0);
            assertThat(result.hasNext()).isFalse();
        }

        @Test
        @DisplayName("존재하지 않거나 삭제된 스페이스면 SPACE_NOT_FOUND 예외가 발생하고 멤버·전사 조회를 시도하지 않는다")
        void getTranscripts_throwsWhenSpaceNotFound() {
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.getTranscripts(OWNER_ID, SPACE_ID, PageRequest.of(0, 10)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_NOT_FOUND);
            verifyNoInteractions(memberRepository);
            verifyNoInteractions(audioTranscriptionRepository);
        }

        @Test
        @DisplayName("요청자가 해당 스페이스 멤버가 아니면 SPACE_ACCESS_DENIED 예외가 발생하고 전사 조회를 시도하지 않는다")
        void getTranscripts_throwsWhenRequesterIsNotMember() {
            Long nonMemberId = 99L;
            Team team = activeTeam(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, nonMemberId)).willReturn(false);

            assertThatThrownBy(() -> reportService.getTranscripts(nonMemberId, SPACE_ID, PageRequest.of(0, 10)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_ACCESS_DENIED);
            verifyNoInteractions(audioTranscriptionRepository);
        }

        @Test
        @DisplayName("GUEST 등 OWNER가 아닌 멤버도 정상 조회된다(오너 제한 없음)")
        void getTranscripts_allowsNonOwnerMemberRequester() {
            // 요청자(guestUserId)는 team.ownerId(OWNER_ID)가 아니지만, 스페이스 멤버이기만 하면 조회를 허용한다.
            Long guestUserId = 55L;
            Team team = activeTeam(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, guestUserId)).willReturn(true);

            Pageable pageable = PageRequest.of(0, 10);
            Page<TranscriptSummaryDto> page = new PageImpl<>(List.of(), pageable, 0);
            given(audioTranscriptionRepository.findAllByTeamId(SPACE_ID, pageable)).willReturn(page);

            PageResponse<TranscriptSummaryDto> result = reportService.getTranscripts(guestUserId, SPACE_ID, pageable);

            assertThat(result.content()).isEmpty();
        }

        @Test
        @DisplayName("page=1, size=5 요청 시 Repository에 동일한 페이지 파라미터가 그대로 전달된다")
        void getTranscripts_passesPageableToRepository() {
            Team team = activeTeam(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);

            Pageable requested = PageRequest.of(1, 5);
            Page<TranscriptSummaryDto> page = new PageImpl<>(List.of(), requested, 0);
            given(audioTranscriptionRepository.findAllByTeamId(eq(SPACE_ID), any(Pageable.class))).willReturn(page);

            reportService.getTranscripts(OWNER_ID, SPACE_ID, requested);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(audioTranscriptionRepository).findAllByTeamId(eq(SPACE_ID), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
            assertThat(captor.getValue().getPageSize()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("REPORTS-02 전사 상세 조회")
    class GetTranscript {

        private static final String TRANSCRIPT_JSON =
                "[{\"speaker\":\"ssong123\",\"start\":12.5,\"end\":15.8,\"text\":\"그럼 다음 안건으로 넘어가겠습니다\"}]";

        @Test
        @DisplayName("정상 조회 시 transcript가 JSON 배열 원문 그대로 응답 DTO에 매핑된다")
        void getTranscript_returnsMappedDetail() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            OffsetDateTime createdAt = OffsetDateTime.parse("2026-08-04T05:00:00Z");
            AudioTranscription transcription = transcriptionOf(MEETING_ID, TRANSCRIPT_JSON, createdAt);
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);
            given(audioTranscriptionRepository.findById(MEETING_ID)).willReturn(Optional.of(transcription));

            TranscriptDetailDto result = reportService.getTranscript(OWNER_ID, MEETING_ID);

            assertThat(result.meetingId()).isEqualTo(MEETING_ID);
            assertThat(result.transcript()).isEqualTo(TRANSCRIPT_JSON);
            assertThat(result.createdAt()).isEqualTo(createdAt);
        }

        @Test
        @DisplayName("존재하지 않는 회의면 MEETING_NOT_FOUND 예외가 발생하고 멤버·전사 조회를 시도하지 않는다")
        void getTranscript_throwsWhenMeetingNotFound() {
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.getTranscript(OWNER_ID, MEETING_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MEETING_NOT_FOUND);
            verifyNoInteractions(memberRepository);
            verifyNoInteractions(audioTranscriptionRepository);
        }

        @Test
        @DisplayName("요청자가 회의가 속한 스페이스의 멤버가 아니면 SPACE_ACCESS_DENIED 예외가 발생하고 전사 조회를 시도하지 않는다")
        void getTranscript_throwsWhenRequesterIsNotMember() {
            Long nonMemberId = 99L;
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, nonMemberId)).willReturn(false);

            assertThatThrownBy(() -> reportService.getTranscript(nonMemberId, MEETING_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_ACCESS_DENIED);
            verifyNoInteractions(audioTranscriptionRepository);
        }

        @Test
        @DisplayName("GUEST 등 OWNER가 아닌 멤버도 정상 조회된다(오너 제한 없음)")
        void getTranscript_allowsNonOwnerMemberRequester() {
            Long guestUserId = 55L;
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            OffsetDateTime createdAt = OffsetDateTime.parse("2026-08-04T05:00:00Z");
            AudioTranscription transcription = transcriptionOf(MEETING_ID, TRANSCRIPT_JSON, createdAt);
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, guestUserId)).willReturn(true);
            given(audioTranscriptionRepository.findById(MEETING_ID)).willReturn(Optional.of(transcription));

            TranscriptDetailDto result = reportService.getTranscript(guestUserId, MEETING_ID);

            assertThat(result.meetingId()).isEqualTo(MEETING_ID);
        }

        @Test
        @DisplayName("회의는 있지만 전사가 없으면(처리 중) TRANSCRIPT_NOT_FOUND 예외가 발생한다")
        void getTranscript_throwsWhenTranscriptNotFound() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);
            given(audioTranscriptionRepository.findById(MEETING_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.getTranscript(OWNER_ID, MEETING_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.TRANSCRIPT_NOT_FOUND);
        }
    }
}

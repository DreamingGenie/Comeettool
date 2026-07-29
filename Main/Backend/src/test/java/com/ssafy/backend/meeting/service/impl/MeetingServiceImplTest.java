package com.ssafy.backend.meeting.service.impl;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.meeting.dto.RequestTransferHostDto;
import com.ssafy.backend.meeting.dto.ResponseTransferHostDto;
import com.ssafy.backend.meeting.entity.MeetingRoom;
import com.ssafy.backend.meeting.entity.Participant;
import com.ssafy.backend.meeting.repository.MeetingRoomRepository;
import com.ssafy.backend.meeting.repository.ParticipantRepository;
import com.ssafy.backend.space.entity.Member;
import com.ssafy.backend.space.entity.MemberAuthority;
import com.ssafy.backend.space.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * MEET-06 호스트 양도 서비스의 권한 검사와 host_id 변경 흐름을 검증하는 순수 단위 테스트.
 *
 * <p>검증 내용:</p>
 * <ul>
 *     <li>현재 Host만 같은 회의의 Participant에게 Host를 양도할 수 있는지 확인한다.</li>
 *     <li>Participant의 memberId를 Member.userId로 변환해 MeetingRoom.hostId에 저장하는지 확인한다.</li>
 *     <li>회의·대상 참여자·대상 멤버가 없거나 대상이 다른 팀이면 양도를 거부하는지 확인한다.</li>
 *     <li>현재 Host 자신에게 다시 양도하는 중복 요청을 거부하는지 확인한다.</li>
 * </ul>
 *
 * <p>Repository는 모두 Mock으로 대체하며 DB와 Spring Context 없이 서비스 로직만 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MeetingServiceImpl MEET-06 단위 테스트")
class MeetingServiceImplTest {

    private static final Long MEETING_ID = 1L;
    private static final Long TEAM_ID = 10L;
    private static final Long CURRENT_HOST_USER_ID = 100L;
    private static final Long NEXT_HOST_USER_ID = 200L;
    private static final Long NEXT_HOST_PARTICIPANT_ID = 20L;
    private static final Long NEXT_HOST_MEMBER_ID = 30L;

    @Mock
    private MeetingRoomRepository meetingRoomRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MeetingServiceImpl meetingService;

    @Nested
    @DisplayName("MEET-06 호스트 양도")
    class TransferHost {

        @Test
        @DisplayName("현재 호스트가 같은 회의 참여자에게 호스트를 양도한다")
        void transferHost_changesMeetingRoomHostId() {
            MeetingRoom meetingRoom = meetingRoom(CURRENT_HOST_USER_ID);
            Participant nextHostParticipant = participant(NEXT_HOST_MEMBER_ID);
            Member nextHostMember = member(NEXT_HOST_MEMBER_ID, NEXT_HOST_USER_ID, TEAM_ID);
            given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                    .willReturn(Optional.of(meetingRoom));
            given(participantRepository.findByIdAndMeetingRoomId(NEXT_HOST_PARTICIPANT_ID, MEETING_ID))
                    .willReturn(Optional.of(nextHostParticipant));
            given(memberRepository.findById(NEXT_HOST_MEMBER_ID)).willReturn(Optional.of(nextHostMember));

            ResponseTransferHostDto response = meetingService.transferHost(
                    CURRENT_HOST_USER_ID,
                    MEETING_ID,
                    request()
            );

            assertThat(meetingRoom.getHostId()).isEqualTo(NEXT_HOST_USER_ID);
            assertThat(response.meetingId()).isEqualTo(MEETING_ID);
            assertThat(response.previousHostId()).isEqualTo(CURRENT_HOST_USER_ID);
            assertThat(response.nextHostId()).isEqualTo(NEXT_HOST_USER_ID);
            verify(meetingRoomRepository, never()).save(any(MeetingRoom.class));
        }

        @Test
        @DisplayName("회의가 없거나 삭제됐으면 MEETING_NOT_FOUND 예외가 발생한다")
        void transferHost_throwsWhenMeetingNotFound() {
            given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID)).willReturn(Optional.empty());

            assertErrorCode(
                    () -> meetingService.transferHost(CURRENT_HOST_USER_ID, MEETING_ID, request()),
                    ErrorCode.MEETING_NOT_FOUND
            );
            verify(participantRepository, never()).findByIdAndMeetingRoomId(any(), any());
        }

        @Test
        @DisplayName("요청자가 현재 호스트가 아니면 MEETING_HOST_REQUIRED 예외가 발생한다")
        void transferHost_throwsWhenRequesterIsNotHost() {
            MeetingRoom meetingRoom = meetingRoom(999L);
            given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                    .willReturn(Optional.of(meetingRoom));

            assertErrorCode(
                    () -> meetingService.transferHost(CURRENT_HOST_USER_ID, MEETING_ID, request()),
                    ErrorCode.MEETING_HOST_REQUIRED
            );
            verify(participantRepository, never()).findByIdAndMeetingRoomId(any(), any());
        }

        @Test
        @DisplayName("새 호스트가 해당 회의 참여자가 아니면 MEETING_PARTICIPANT_NOT_FOUND 예외가 발생한다")
        void transferHost_throwsWhenTargetIsNotParticipant() {
            MeetingRoom meetingRoom = meetingRoom(CURRENT_HOST_USER_ID);
            given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                    .willReturn(Optional.of(meetingRoom));
            given(participantRepository.findByIdAndMeetingRoomId(NEXT_HOST_PARTICIPANT_ID, MEETING_ID))
                    .willReturn(Optional.empty());

            assertErrorCode(
                    () -> meetingService.transferHost(CURRENT_HOST_USER_ID, MEETING_ID, request()),
                    ErrorCode.MEETING_PARTICIPANT_NOT_FOUND
            );
            assertThat(meetingRoom.getHostId()).isEqualTo(CURRENT_HOST_USER_ID);
            verify(memberRepository, never()).findById(any());
        }

        @Test
        @DisplayName("참여자에 연결된 멤버가 없으면 MEETING_PARTICIPANT_NOT_FOUND 예외가 발생한다")
        void transferHost_throwsWhenTargetMemberNotFound() {
            MeetingRoom meetingRoom = meetingRoom(CURRENT_HOST_USER_ID);
            Participant nextHostParticipant = participant(NEXT_HOST_MEMBER_ID);
            given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                    .willReturn(Optional.of(meetingRoom));
            given(participantRepository.findByIdAndMeetingRoomId(NEXT_HOST_PARTICIPANT_ID, MEETING_ID))
                    .willReturn(Optional.of(nextHostParticipant));
            given(memberRepository.findById(NEXT_HOST_MEMBER_ID)).willReturn(Optional.empty());

            assertErrorCode(
                    () -> meetingService.transferHost(CURRENT_HOST_USER_ID, MEETING_ID, request()),
                    ErrorCode.MEETING_PARTICIPANT_NOT_FOUND
            );
            assertThat(meetingRoom.getHostId()).isEqualTo(CURRENT_HOST_USER_ID);
        }

        @Test
        @DisplayName("대상 멤버가 회의의 상위 팀 소속이 아니면 양도를 거부한다")
        void transferHost_throwsWhenTargetMemberBelongsToAnotherTeam() {
            MeetingRoom meetingRoom = meetingRoom(CURRENT_HOST_USER_ID);
            Participant nextHostParticipant = participant(NEXT_HOST_MEMBER_ID);
            Member anotherTeamMember = member(NEXT_HOST_MEMBER_ID, NEXT_HOST_USER_ID, 999L);
            given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                    .willReturn(Optional.of(meetingRoom));
            given(participantRepository.findByIdAndMeetingRoomId(NEXT_HOST_PARTICIPANT_ID, MEETING_ID))
                    .willReturn(Optional.of(nextHostParticipant));
            given(memberRepository.findById(NEXT_HOST_MEMBER_ID)).willReturn(Optional.of(anotherTeamMember));

            assertErrorCode(
                    () -> meetingService.transferHost(CURRENT_HOST_USER_ID, MEETING_ID, request()),
                    ErrorCode.MEETING_PARTICIPANT_NOT_FOUND
            );
            assertThat(meetingRoom.getHostId()).isEqualTo(CURRENT_HOST_USER_ID);
        }

        @Test
        @DisplayName("현재 호스트 자신을 다시 지정하면 MEETING_HOST_ALREADY_ASSIGNED 예외가 발생한다")
        void transferHost_throwsWhenTargetIsCurrentHost() {
            MeetingRoom meetingRoom = meetingRoom(CURRENT_HOST_USER_ID);
            Participant currentHostParticipant = participant(NEXT_HOST_MEMBER_ID);
            Member currentHostMember = member(NEXT_HOST_MEMBER_ID, CURRENT_HOST_USER_ID, TEAM_ID);
            given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                    .willReturn(Optional.of(meetingRoom));
            given(participantRepository.findByIdAndMeetingRoomId(NEXT_HOST_PARTICIPANT_ID, MEETING_ID))
                    .willReturn(Optional.of(currentHostParticipant));
            given(memberRepository.findById(NEXT_HOST_MEMBER_ID)).willReturn(Optional.of(currentHostMember));

            assertErrorCode(
                    () -> meetingService.transferHost(CURRENT_HOST_USER_ID, MEETING_ID, request()),
                    ErrorCode.MEETING_HOST_ALREADY_ASSIGNED
            );
            assertThat(meetingRoom.getHostId()).isEqualTo(CURRENT_HOST_USER_ID);
        }
    }

    private RequestTransferHostDto request() {
        return new RequestTransferHostDto(NEXT_HOST_PARTICIPANT_ID);
    }

    private MeetingRoom meetingRoom(Long hostUserId) {
        MeetingRoom meetingRoom = MeetingRoom.builder()
                .teamId(TEAM_ID)
                .hostId(hostUserId)
                .name("주간 회의")
                .build();
        ReflectionTestUtils.setField(meetingRoom, "id", MEETING_ID);
        return meetingRoom;
    }

    private Participant participant(Long memberId) {
        Participant participant = Participant.builder()
                .meetingRoomId(MEETING_ID)
                .memberId(memberId)
                .participantRole("BE")
                .build();
        ReflectionTestUtils.setField(participant, "id", NEXT_HOST_PARTICIPANT_ID);
        return participant;
    }

    private Member member(Long memberId, Long userId, Long teamId) {
        Member member = Member.builder()
                .userId(userId)
                .teamId(teamId)
                .authority(MemberAuthority.MEMBER)
                .nickname("새 호스트")
                .build();
        ReflectionTestUtils.setField(member, "id", memberId);
        return member;
    }

    private void assertErrorCode(Runnable action, ErrorCode expectedErrorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(expectedErrorCode);
    }
}

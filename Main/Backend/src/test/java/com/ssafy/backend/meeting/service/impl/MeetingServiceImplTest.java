package com.ssafy.backend.meeting.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

/**
 * MEET-06 호스트 양도 서비스 단위 테스트.
 * 저장소는 Mock으로 분리하고 호스트·참여자·팀 검증과 hostId 변경 결과를 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MEET-06 호스트 양도 서비스 테스트")
class MeetingServiceImplTest {

    private static final Long MEETING_ID = 100L;
    private static final Long TEAM_ID = 10L;
    private static final Long CURRENT_HOST_USER_ID = 1L;
    private static final Long NEXT_HOST_PARTICIPANT_ID = 30L;
    private static final Long NEXT_HOST_MEMBER_ID = 20L;
    private static final Long NEXT_HOST_USER_ID = 2L;

    @Mock
    private MeetingRoomRepository meetingRoomRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MeetingServiceImpl meetingService;

    private MeetingRoom meetingRoom;
    private Participant nextHostParticipant;
    private Member nextHostMember;
    private RequestTransferHostDto request;

    @BeforeEach
    void setUp() {
        meetingRoom = createMeetingRoom(CURRENT_HOST_USER_ID);
        nextHostParticipant = createParticipant(NEXT_HOST_MEMBER_ID);
        nextHostMember = createMember(NEXT_HOST_USER_ID, TEAM_ID);
        request = new RequestTransferHostDto(NEXT_HOST_PARTICIPANT_ID);
    }

    @Test
    @DisplayName("현재 호스트가 같은 회의·팀 참여자에게 호스트를 양도한다")
    void transferHost_changesMeetingHostAndReturnsResult() {
        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.of(meetingRoom));
        given(participantRepository.findByIdAndMeetingRoomId(NEXT_HOST_PARTICIPANT_ID, MEETING_ID))
                .willReturn(Optional.of(nextHostParticipant));
        given(memberRepository.findById(NEXT_HOST_MEMBER_ID))
                .willReturn(Optional.of(nextHostMember));

        ResponseTransferHostDto response =
                meetingService.transferHost(CURRENT_HOST_USER_ID, MEETING_ID, request);

        assertThat(meetingRoom.getHostId()).isEqualTo(NEXT_HOST_USER_ID);
        assertThat(response.meetingId()).isEqualTo(MEETING_ID);
        assertThat(response.previousHostId()).isEqualTo(CURRENT_HOST_USER_ID);
        assertThat(response.nextHostId()).isEqualTo(NEXT_HOST_USER_ID);
        verify(meetingRoomRepository).findActiveByIdForUpdate(MEETING_ID);
        verify(participantRepository)
                .findByIdAndMeetingRoomId(NEXT_HOST_PARTICIPANT_ID, MEETING_ID);
        verify(memberRepository).findById(NEXT_HOST_MEMBER_ID);
    }

    @Test
    @DisplayName("활성 회의가 없으면 MEETING_NOT_FOUND 예외가 발생한다")
    void transferHost_rejectsMissingMeeting() {
        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.empty());

        assertErrorCode(
                () -> meetingService.transferHost(CURRENT_HOST_USER_ID, MEETING_ID, request),
                ErrorCode.MEETING_NOT_FOUND
        );
        verifyNoInteractions(participantRepository, memberRepository);
    }

    @Test
    @DisplayName("요청자가 현재 호스트가 아니면 양도할 수 없다")
    void transferHost_rejectsNonHostRequester() {
        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.of(meetingRoom));

        assertErrorCode(
                () -> meetingService.transferHost(999L, MEETING_ID, request),
                ErrorCode.MEETING_HOST_REQUIRED
        );
        verifyNoInteractions(participantRepository, memberRepository);
    }

    @Test
    @DisplayName("양도 대상이 해당 회의의 참여자가 아니면 거부한다")
    void transferHost_rejectsParticipantOutsideMeeting() {
        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.of(meetingRoom));
        given(participantRepository.findByIdAndMeetingRoomId(NEXT_HOST_PARTICIPANT_ID, MEETING_ID))
                .willReturn(Optional.empty());

        assertErrorCode(
                () -> meetingService.transferHost(CURRENT_HOST_USER_ID, MEETING_ID, request),
                ErrorCode.MEETING_PARTICIPANT_NOT_FOUND
        );
        verifyNoInteractions(memberRepository);
    }

    @Test
    @DisplayName("참여자에 연결된 팀 멤버가 없으면 거부한다")
    void transferHost_rejectsMissingMember() {
        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.of(meetingRoom));
        given(participantRepository.findByIdAndMeetingRoomId(NEXT_HOST_PARTICIPANT_ID, MEETING_ID))
                .willReturn(Optional.of(nextHostParticipant));
        given(memberRepository.findById(NEXT_HOST_MEMBER_ID))
                .willReturn(Optional.empty());

        assertErrorCode(
                () -> meetingService.transferHost(CURRENT_HOST_USER_ID, MEETING_ID, request),
                ErrorCode.MEETING_PARTICIPANT_NOT_FOUND
        );
    }

    @Test
    @DisplayName("참여자가 회의와 다른 팀 소속이면 거부한다")
    void transferHost_rejectsMemberFromDifferentTeam() {
        Member otherTeamMember = createMember(NEXT_HOST_USER_ID, 999L);
        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.of(meetingRoom));
        given(participantRepository.findByIdAndMeetingRoomId(NEXT_HOST_PARTICIPANT_ID, MEETING_ID))
                .willReturn(Optional.of(nextHostParticipant));
        given(memberRepository.findById(NEXT_HOST_MEMBER_ID))
                .willReturn(Optional.of(otherTeamMember));

        assertErrorCode(
                () -> meetingService.transferHost(CURRENT_HOST_USER_ID, MEETING_ID, request),
                ErrorCode.MEETING_PARTICIPANT_NOT_FOUND
        );
    }

    @Test
    @DisplayName("현재 호스트 자신을 다시 지정하면 거부한다")
    void transferHost_rejectsAlreadyAssignedHost() {
        Member currentHostMember = createMember(CURRENT_HOST_USER_ID, TEAM_ID);
        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.of(meetingRoom));
        given(participantRepository.findByIdAndMeetingRoomId(NEXT_HOST_PARTICIPANT_ID, MEETING_ID))
                .willReturn(Optional.of(nextHostParticipant));
        given(memberRepository.findById(NEXT_HOST_MEMBER_ID))
                .willReturn(Optional.of(currentHostMember));

        assertErrorCode(
                () -> meetingService.transferHost(CURRENT_HOST_USER_ID, MEETING_ID, request),
                ErrorCode.MEETING_HOST_ALREADY_ASSIGNED
        );
    }

    private void assertErrorCode(Runnable action, ErrorCode expectedErrorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CustomException.class)
                .satisfies(exception ->
                        assertThat(((CustomException) exception).getErrorCode())
                                .isEqualTo(expectedErrorCode));
    }

    private MeetingRoom createMeetingRoom(Long hostUserId) {
        return MeetingRoom.builder()
                .teamId(TEAM_ID)
                .hostId(hostUserId)
                .name("데일리 미팅")
                .build();
    }

    private Participant createParticipant(Long memberId) {
        return Participant.builder()
                .meetingRoomId(MEETING_ID)
                .memberId(memberId)
                .participantRole("BE")
                .build();
    }

    private Member createMember(Long userId, Long teamId) {
        return Member.builder()
                .userId(userId)
                .teamId(teamId)
                .authority(MemberAuthority.MEMBER)
                .nickname("참여자")
                .build();
    }
}

package com.ssafy.backend.meeting.service.impl;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.meeting.dto.RequestCreateMeetingDto;
import com.ssafy.backend.meeting.dto.RequestTransferHostDto;
import com.ssafy.backend.meeting.dto.ResponseCreateMeetingDto;
import com.ssafy.backend.meeting.dto.ResponseJoinMeetingDto;
import com.ssafy.backend.meeting.dto.ResponseLeaveMeetingDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingHostDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingInvitationDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingInviteCandidateDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingListDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingParticipantDto;
import com.ssafy.backend.meeting.dto.ResponseTransferHostDto;
import com.ssafy.backend.meeting.entity.MeetingRoom;
import com.ssafy.backend.meeting.entity.Participant;
import com.ssafy.backend.meeting.event.MeetingTranscriptionEndedEvent;
import com.ssafy.backend.meeting.event.MeetingTranscriptionStartedEvent;
import com.ssafy.backend.meeting.livekit.LiveKitConnectionInfo;
import com.ssafy.backend.meeting.livekit.LiveKitParticipantManager;
import com.ssafy.backend.meeting.livekit.LiveKitRoomManager;
import com.ssafy.backend.meeting.livekit.LiveKitTokenProvider;
import com.ssafy.backend.meeting.mapper.MeetingMapper;
import com.ssafy.backend.meeting.repository.MeetingRoomRepository;
import com.ssafy.backend.meeting.repository.ParticipantRepository;
import com.ssafy.backend.member.entity.Member;
import com.ssafy.backend.member.entity.MemberAuthority;
import com.ssafy.backend.member.repository.MemberRepository;
import com.ssafy.backend.space.entity.Team;
import com.ssafy.backend.space.repository.TeamRepository;
import com.ssafy.backend.user.entity.User;
import com.ssafy.backend.user.repository.UserRepository;

/**
 * 회의 서비스 단위 테스트.
 * 저장소와 LiveKit 토큰 발급기를 Mock으로 분리하고
 * MEET-01·02·03·04·05·06·07·08·09·10의 정상 흐름과 접근 제한을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("회의 서비스 테스트")
class MeetingServiceImplTest {

    private static final Long MEETING_ID = 100L;
    private static final Long TEAM_ID = 10L;
    private static final Long CURRENT_HOST_USER_ID = 1L;
    private static final Long CURRENT_HOST_MEMBER_ID = 11L;
    private static final Long NEXT_HOST_PARTICIPANT_ID = 30L;
    private static final Long NEXT_HOST_MEMBER_ID = 20L;
    private static final Long NEXT_HOST_USER_ID = 2L;
    private static final OffsetDateTime CREATED_AT =
            OffsetDateTime.parse("2026-07-30T12:00:00+09:00");

    @Mock
    private MeetingRoomRepository meetingRoomRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private MeetingMapper meetingMapper;

    @Mock
    private LiveKitTokenProvider liveKitTokenProvider;

    @Mock
    private LiveKitParticipantManager liveKitParticipantManager;

    @Mock
    private LiveKitRoomManager liveKitRoomManager;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

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
    @DisplayName("활성 회의가 2개이면 회의와 미접속 Host Participant를 생성한다")
    void addMeeting_createsMeetingAndDisconnectedHostParticipant() {
        Team team = createTeam();
        Member hostMember = createMemberWithTeamRoleId(
                CURRENT_HOST_MEMBER_ID,
                CURRENT_HOST_USER_ID,
                TEAM_ID,
                50L
        );
        MeetingRoom savedMeetingRoom = createSavedMeetingRoom();
        RequestCreateMeetingDto createRequest = new RequestCreateMeetingDto("데일리 미팅");
        ResponseCreateMeetingDto expectedResponse = createCreateMeetingResponse();

        given(teamRepository.findActiveByIdForUpdate(TEAM_ID))
                .willReturn(Optional.of(team));
        given(memberRepository.findByTeamIdAndUserId(TEAM_ID, CURRENT_HOST_USER_ID))
                .willReturn(Optional.of(hostMember));
        given(meetingRoomRepository.countByTeamIdAndIsDeletedFalse(TEAM_ID))
                .willReturn(2L);
        given(meetingRoomRepository.save(any(MeetingRoom.class)))
                .willReturn(savedMeetingRoom);
        given(memberRepository.findTeamRoleNameByMemberId(CURRENT_HOST_MEMBER_ID))
                .willReturn(Optional.of("BE"));
        given(meetingMapper.toCreateResponse(
                savedMeetingRoom,
                CURRENT_HOST_USER_ID,
                1
        )).willReturn(expectedResponse);

        ResponseCreateMeetingDto response =
                meetingService.addMeeting(CURRENT_HOST_USER_ID, TEAM_ID, createRequest);

        ArgumentCaptor<MeetingRoom> meetingRoomCaptor =
                ArgumentCaptor.forClass(MeetingRoom.class);
        ArgumentCaptor<Participant> participantCaptor =
                ArgumentCaptor.forClass(Participant.class);
        verify(meetingRoomRepository).save(meetingRoomCaptor.capture());
        verify(participantRepository).save(participantCaptor.capture());

        MeetingRoom createdMeetingRoom = meetingRoomCaptor.getValue();
        assertThat(createdMeetingRoom.getTeamId()).isEqualTo(TEAM_ID);
        assertThat(createdMeetingRoom.getHostId()).isEqualTo(CURRENT_HOST_USER_ID);
        assertThat(createdMeetingRoom.getName()).isEqualTo("데일리 미팅");
        assertThat(createdMeetingRoom.isDeleted()).isFalse();

        Participant createdParticipant = participantCaptor.getValue();
        assertThat(createdParticipant.getMeetingRoomId()).isEqualTo(MEETING_ID);
        assertThat(createdParticipant.getMemberId()).isEqualTo(CURRENT_HOST_MEMBER_ID);
        assertThat(createdParticipant.getParticipantRole()).isEqualTo("BE");
        assertThat(createdParticipant.isInMeeting()).isFalse();

        ArgumentCaptor<MeetingTranscriptionStartedEvent> eventCaptor =
                ArgumentCaptor.forClass(
                        MeetingTranscriptionStartedEvent.class
                );
        verify(applicationEventPublisher)
                .publishEvent(eventCaptor.capture());

        MeetingTranscriptionStartedEvent event = eventCaptor.getValue();
        assertThat(event.meetingId()).isEqualTo(MEETING_ID);
        assertThat(event.startedAt()).isEqualTo(
                CREATED_AT.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        );
        assertThat(response).isEqualTo(expectedResponse);
    }

    @Test
    @DisplayName("팀 역할이 없는 Host는 역할을 null로 저장한다")
    void addMeeting_savesNullParticipantRoleWhenHostHasNoTeamRole() {
        Team team = createTeam();
        Member hostMember = createMemberWithTeamRoleId(
                CURRENT_HOST_MEMBER_ID,
                CURRENT_HOST_USER_ID,
                TEAM_ID,
                null
        );
        MeetingRoom savedMeetingRoom = createSavedMeetingRoom();
        RequestCreateMeetingDto createRequest = new RequestCreateMeetingDto("데일리 미팅");
        ResponseCreateMeetingDto expectedResponse = createCreateMeetingResponse();

        given(teamRepository.findActiveByIdForUpdate(TEAM_ID))
                .willReturn(Optional.of(team));
        given(memberRepository.findByTeamIdAndUserId(TEAM_ID, CURRENT_HOST_USER_ID))
                .willReturn(Optional.of(hostMember));
        given(meetingRoomRepository.countByTeamIdAndIsDeletedFalse(TEAM_ID))
                .willReturn(0L);
        given(meetingRoomRepository.save(any(MeetingRoom.class)))
                .willReturn(savedMeetingRoom);
        given(memberRepository.findTeamRoleNameByMemberId(CURRENT_HOST_MEMBER_ID))
                .willReturn(Optional.empty());
        given(meetingMapper.toCreateResponse(
                savedMeetingRoom,
                CURRENT_HOST_USER_ID,
                1
        )).willReturn(expectedResponse);

        meetingService.addMeeting(CURRENT_HOST_USER_ID, TEAM_ID, createRequest);

        ArgumentCaptor<Participant> participantCaptor =
                ArgumentCaptor.forClass(Participant.class);
        verify(participantRepository).save(participantCaptor.capture());
        assertThat(participantCaptor.getValue().getParticipantRole()).isNull();
    }

    @Test
    @DisplayName("스페이스가 없으면 SPACE_NOT_FOUND 예외가 발생한다")
    void addMeeting_rejectsMissingSpace() {
        RequestCreateMeetingDto createRequest = new RequestCreateMeetingDto("데일리 미팅");
        given(teamRepository.findActiveByIdForUpdate(TEAM_ID))
                .willReturn(Optional.empty());

        assertErrorCode(
                () -> meetingService.addMeeting(CURRENT_HOST_USER_ID, TEAM_ID, createRequest),
                ErrorCode.SPACE_NOT_FOUND
        );
        verifyNoInteractions(
                memberRepository,
                meetingRoomRepository,
                participantRepository,
                meetingMapper
        );
    }

    @Test
    @DisplayName("스페이스 멤버가 아니면 SPACE_ACCESS_DENIED 예외가 발생한다")
    void addMeeting_rejectsRequesterOutsideSpace() {
        RequestCreateMeetingDto createRequest = new RequestCreateMeetingDto("데일리 미팅");
        given(teamRepository.findActiveByIdForUpdate(TEAM_ID))
                .willReturn(Optional.of(createTeam()));
        given(memberRepository.findByTeamIdAndUserId(TEAM_ID, CURRENT_HOST_USER_ID))
                .willReturn(Optional.empty());

        assertErrorCode(
                () -> meetingService.addMeeting(CURRENT_HOST_USER_ID, TEAM_ID, createRequest),
                ErrorCode.SPACE_ACCESS_DENIED
        );
        verifyNoInteractions(meetingRoomRepository, participantRepository, meetingMapper);
    }

    @Test
    @DisplayName("게스트는 회의를 생성할 수 없다")
    void addMeeting_rejectsGuestRequester() {
        Member guestMember = createMemberWithAuthority(
                CURRENT_HOST_MEMBER_ID,
                CURRENT_HOST_USER_ID,
                TEAM_ID,
                MemberAuthority.GUEST,
                null
        );
        RequestCreateMeetingDto createRequest = new RequestCreateMeetingDto("데일리 미팅");
        given(teamRepository.findActiveByIdForUpdate(TEAM_ID))
                .willReturn(Optional.of(createTeam()));
        given(memberRepository.findByTeamIdAndUserId(TEAM_ID, CURRENT_HOST_USER_ID))
                .willReturn(Optional.of(guestMember));

        assertErrorCode(
                () -> meetingService.addMeeting(CURRENT_HOST_USER_ID, TEAM_ID, createRequest),
                ErrorCode.MEETING_CREATE_FORBIDDEN
        );
        verifyNoInteractions(meetingRoomRepository, participantRepository, meetingMapper);
        verify(memberRepository, never()).findTeamRoleNameByMemberId(any(Long.class));
    }

    @Test
    @DisplayName("활성 회의가 3개이면 MEETING_ROOM_LIMIT_EXCEEDED 예외가 발생한다")
    void addMeeting_rejectsWhenActiveMeetingRoomLimitIsExceeded() {
        Member hostMember = createMemberWithTeamRoleId(
                CURRENT_HOST_MEMBER_ID,
                CURRENT_HOST_USER_ID,
                TEAM_ID,
                null
        );
        RequestCreateMeetingDto createRequest = new RequestCreateMeetingDto("데일리 미팅");
        given(teamRepository.findActiveByIdForUpdate(TEAM_ID))
                .willReturn(Optional.of(createTeam()));
        given(memberRepository.findByTeamIdAndUserId(TEAM_ID, CURRENT_HOST_USER_ID))
                .willReturn(Optional.of(hostMember));
        given(meetingRoomRepository.countByTeamIdAndIsDeletedFalse(TEAM_ID))
                .willReturn(3L);

        assertErrorCode(
                () -> meetingService.addMeeting(CURRENT_HOST_USER_ID, TEAM_ID, createRequest),
                ErrorCode.MEETING_ROOM_LIMIT_EXCEEDED
        );
        verify(meetingRoomRepository, never()).save(any(MeetingRoom.class));
        verifyNoInteractions(participantRepository, meetingMapper);
    }

    @Test
    @DisplayName("참가 권한이 있는 진행 중 회의를 최신순으로 조회한다")
    void getParticipatingMeetings_returnsActiveMeetingsWithConnectionState() {
        Long secondMeetingId = 101L;
        Member requesterMember = createMemberWithId(
                CURRENT_HOST_MEMBER_ID,
                CURRENT_HOST_USER_ID,
                TEAM_ID,
                "요청자"
        );
        Participant connectedParticipant = createParticipantForMeeting(
                30L,
                MEETING_ID,
                CURRENT_HOST_MEMBER_ID,
                true
        );
        Participant disconnectedParticipant = createParticipantForMeeting(
                31L,
                secondMeetingId,
                CURRENT_HOST_MEMBER_ID,
                false
        );
        MeetingRoom olderMeeting = createSavedMeetingRoom(
                MEETING_ID,
                CURRENT_HOST_USER_ID,
                "데일리 미팅",
                CREATED_AT
        );
        MeetingRoom newerMeeting = createSavedMeetingRoom(
                secondMeetingId,
                NEXT_HOST_USER_ID,
                "기획 회의",
                CREATED_AT.plusHours(1)
        );
        ResponseMeetingListDto newerResponse = new ResponseMeetingListDto(
                secondMeetingId,
                TEAM_ID,
                "기획 회의",
                new ResponseMeetingHostDto(NEXT_HOST_USER_ID),
                CREATED_AT.plusHours(1),
                0L,
                false
        );
        ResponseMeetingListDto olderResponse = new ResponseMeetingListDto(
                MEETING_ID,
                TEAM_ID,
                "데일리 미팅",
                new ResponseMeetingHostDto(CURRENT_HOST_USER_ID),
                CREATED_AT,
                2L,
                true
        );

        given(teamRepository.findByIdAndIsDeletedFalse(TEAM_ID))
                .willReturn(Optional.of(createTeam()));
        given(memberRepository.findByTeamIdAndUserId(TEAM_ID, CURRENT_HOST_USER_ID))
                .willReturn(Optional.of(requesterMember));
        given(participantRepository.findAllByMemberIdOrderByIdAsc(CURRENT_HOST_MEMBER_ID))
                .willReturn(List.of(connectedParticipant, disconnectedParticipant));
        given(meetingRoomRepository.findAllActiveByIdsAndTeamId(any(), eq(TEAM_ID)))
                .willReturn(List.of(newerMeeting, olderMeeting));
        given(participantRepository.countInMeetingParticipantsByMeetingRoomIds(any()))
                .willReturn(List.<Object[]>of(new Object[]{MEETING_ID, 2L}));
        given(meetingMapper.toListItem(newerMeeting, 0L, false))
                .willReturn(newerResponse);
        given(meetingMapper.toListItem(olderMeeting, 2L, true))
                .willReturn(olderResponse);

        List<ResponseMeetingListDto> response =
                meetingService.getParticipatingMeetings(CURRENT_HOST_USER_ID, TEAM_ID);

        assertThat(response).containsExactly(newerResponse, olderResponse);
        verify(meetingRoomRepository).findAllActiveByIdsAndTeamId(any(), eq(TEAM_ID));
        verify(participantRepository).countInMeetingParticipantsByMeetingRoomIds(any());
    }

    @Test
    @DisplayName("회의 목록 조회 시 스페이스가 없으면 SPACE_NOT_FOUND 예외가 발생한다")
    void getParticipatingMeetings_rejectsMissingSpace() {
        given(teamRepository.findByIdAndIsDeletedFalse(TEAM_ID))
                .willReturn(Optional.empty());

        assertErrorCode(
                () -> meetingService.getParticipatingMeetings(
                        CURRENT_HOST_USER_ID,
                        TEAM_ID
                ),
                ErrorCode.SPACE_NOT_FOUND
        );
        verifyNoInteractions(
                memberRepository,
                participantRepository,
                meetingRoomRepository,
                meetingMapper
        );
    }

    @Test
    @DisplayName("회의 목록 조회 시 스페이스 멤버가 아니면 접근을 거부한다")
    void getParticipatingMeetings_rejectsRequesterOutsideSpace() {
        given(teamRepository.findByIdAndIsDeletedFalse(TEAM_ID))
                .willReturn(Optional.of(createTeam()));
        given(memberRepository.findByTeamIdAndUserId(TEAM_ID, CURRENT_HOST_USER_ID))
                .willReturn(Optional.empty());

        assertErrorCode(
                () -> meetingService.getParticipatingMeetings(
                        CURRENT_HOST_USER_ID,
                        TEAM_ID
                ),
                ErrorCode.SPACE_ACCESS_DENIED
        );
        verifyNoInteractions(participantRepository, meetingRoomRepository, meetingMapper);
    }

    @Test
    @DisplayName("참가 권한이 있는 회의가 없으면 빈 목록을 반환한다")
    void getParticipatingMeetings_returnsEmptyListWithoutParticipants() {
        Member requesterMember = createMemberWithId(
                CURRENT_HOST_MEMBER_ID,
                CURRENT_HOST_USER_ID,
                TEAM_ID,
                "요청자"
        );
        given(teamRepository.findByIdAndIsDeletedFalse(TEAM_ID))
                .willReturn(Optional.of(createTeam()));
        given(memberRepository.findByTeamIdAndUserId(TEAM_ID, CURRENT_HOST_USER_ID))
                .willReturn(Optional.of(requesterMember));
        given(participantRepository.findAllByMemberIdOrderByIdAsc(CURRENT_HOST_MEMBER_ID))
                .willReturn(List.of());

        List<ResponseMeetingListDto> response =
                meetingService.getParticipatingMeetings(CURRENT_HOST_USER_ID, TEAM_ID);

        assertThat(response).isEmpty();
        verifyNoInteractions(meetingRoomRepository, meetingMapper);
        verify(participantRepository, never())
                .countInMeetingParticipantsByMeetingRoomIds(any());
    }

    @Test
    @DisplayName("참가 회의가 모두 종료되었으면 빈 목록을 반환한다")
    void getParticipatingMeetings_excludesDeletedMeetings() {
        Member requesterMember = createMemberWithId(
                CURRENT_HOST_MEMBER_ID,
                CURRENT_HOST_USER_ID,
                TEAM_ID,
                "요청자"
        );
        Participant participant = createParticipantForMeeting(
                30L,
                MEETING_ID,
                CURRENT_HOST_MEMBER_ID,
                false
        );
        given(teamRepository.findByIdAndIsDeletedFalse(TEAM_ID))
                .willReturn(Optional.of(createTeam()));
        given(memberRepository.findByTeamIdAndUserId(TEAM_ID, CURRENT_HOST_USER_ID))
                .willReturn(Optional.of(requesterMember));
        given(participantRepository.findAllByMemberIdOrderByIdAsc(CURRENT_HOST_MEMBER_ID))
                .willReturn(List.of(participant));
        given(meetingRoomRepository.findAllActiveByIdsAndTeamId(any(), eq(TEAM_ID)))
                .willReturn(List.of());

        List<ResponseMeetingListDto> response =
                meetingService.getParticipatingMeetings(CURRENT_HOST_USER_ID, TEAM_ID);

        assertThat(response).isEmpty();
        verify(participantRepository, never())
                .countInMeetingParticipantsByMeetingRoomIds(any());
        verifyNoInteractions(meetingMapper);
    }

    @Test
    @DisplayName("초대된 참여자는 DB 상태 변경 없이 LiveKit 입장 정보를 발급받는다")
    void joinMeeting_returnsLiveKitConnectionInfoWithoutEnteringParticipant() {
        long participantId = 30L;
        MeetingRoom savedMeetingRoom = createSavedMeetingRoom();
        Member member = createMemberWithId(
                CURRENT_HOST_MEMBER_ID,
                CURRENT_HOST_USER_ID,
                TEAM_ID,
                "호스트"
        );
        Participant participant = createParticipantWithId(
                participantId,
                CURRENT_HOST_MEMBER_ID,
                "BE",
                false
        );
        LiveKitConnectionInfo connectionInfo = new LiveKitConnectionInfo(
                "livekit-token",
                "wss://test.livekit.cloud",
                "meeting-100",
                "participant-30"
        );

        given(meetingRoomRepository.findActiveById(MEETING_ID))
                .willReturn(Optional.of(savedMeetingRoom));
        given(memberRepository.findByTeamIdAndUserId(TEAM_ID, CURRENT_HOST_USER_ID))
                .willReturn(Optional.of(member));
        given(participantRepository.findByMeetingRoomIdAndMemberId(
                MEETING_ID,
                CURRENT_HOST_MEMBER_ID
        )).willReturn(Optional.of(participant));
        given(liveKitTokenProvider.generateJoinToken(
                MEETING_ID,
                participantId,
                "호스트"
        )).willReturn(connectionInfo);

        ResponseJoinMeetingDto response =
                meetingService.joinMeeting(CURRENT_HOST_USER_ID, MEETING_ID);

        assertThat(response.meetingRoomId()).isEqualTo(MEETING_ID);
        assertThat(response.role()).isEqualTo("BE");
        assertThat(response.isHost()).isTrue();
        assertThat(response.token()).isEqualTo("livekit-token");
        assertThat(response.url()).isEqualTo("wss://test.livekit.cloud");
        assertThat(participant.isInMeeting()).isFalse();
    }

    @Test
    @DisplayName("삭제되지 않은 회의를 찾을 수 없으면 입장을 거부한다")
    void joinMeeting_rejectsMissingMeeting() {
        given(meetingRoomRepository.findActiveById(MEETING_ID))
                .willReturn(Optional.empty());

        assertErrorCode(
                () -> meetingService.joinMeeting(CURRENT_HOST_USER_ID, MEETING_ID),
                ErrorCode.MEETING_NOT_FOUND
        );
        verifyNoInteractions(participantRepository, memberRepository, liveKitTokenProvider);
    }

    @Test
    @DisplayName("요청자가 회의의 상위 팀 멤버가 아니면 입장을 거부한다")
    void joinMeeting_rejectsRequesterOutsideTeam() {
        MeetingRoom savedMeetingRoom = createSavedMeetingRoom();
        given(meetingRoomRepository.findActiveById(MEETING_ID))
                .willReturn(Optional.of(savedMeetingRoom));
        given(memberRepository.findByTeamIdAndUserId(TEAM_ID, CURRENT_HOST_USER_ID))
                .willReturn(Optional.empty());

        assertErrorCode(
                () -> meetingService.joinMeeting(CURRENT_HOST_USER_ID, MEETING_ID),
                ErrorCode.MEETING_ACCESS_DENIED
        );
        verifyNoInteractions(participantRepository, liveKitTokenProvider);
    }

    @Test
    @DisplayName("팀 멤버라도 회의에 초대되지 않았으면 입장을 거부한다")
    void joinMeeting_rejectsUninvitedRequester() {
        MeetingRoom savedMeetingRoom = createSavedMeetingRoom();
        Member member = createMemberWithId(
                CURRENT_HOST_MEMBER_ID,
                CURRENT_HOST_USER_ID,
                TEAM_ID,
                "요청자"
        );
        given(meetingRoomRepository.findActiveById(MEETING_ID))
                .willReturn(Optional.of(savedMeetingRoom));
        given(memberRepository.findByTeamIdAndUserId(TEAM_ID, CURRENT_HOST_USER_ID))
                .willReturn(Optional.of(member));
        given(participantRepository.findByMeetingRoomIdAndMemberId(
                MEETING_ID,
                CURRENT_HOST_MEMBER_ID
        )).willReturn(Optional.empty());

        assertErrorCode(
                () -> meetingService.joinMeeting(CURRENT_HOST_USER_ID, MEETING_ID),
                ErrorCode.MEETING_ACCESS_DENIED
        );
        verifyNoInteractions(liveKitTokenProvider);
    }

    @Test
    @DisplayName("입장 중인 비호스트가 퇴장하면 LiveKit 연결 종료를 요청한다")
    void leaveMeeting_disconnectsParticipantWithoutChangingPresence() {
        long participantId = 30L;
        MeetingRoom savedMeetingRoom = createSavedMeetingRoom();
        Member member = createMemberWithId(
                NEXT_HOST_MEMBER_ID,
                NEXT_HOST_USER_ID,
                TEAM_ID,
                "참여자"
        );
        Participant participant = createParticipantWithId(
                participantId,
                NEXT_HOST_MEMBER_ID,
                "BE",
                true
        );

        given(meetingRoomRepository.findActiveById(MEETING_ID))
                .willReturn(Optional.of(savedMeetingRoom));
        given(memberRepository.findByTeamIdAndUserId(
                TEAM_ID,
                NEXT_HOST_USER_ID
        )).willReturn(Optional.of(member));
        given(participantRepository.findByMeetingRoomIdAndMemberId(
                MEETING_ID,
                NEXT_HOST_MEMBER_ID
        )).willReturn(Optional.of(participant));

        ResponseLeaveMeetingDto response =
                meetingService.leaveMeeting(NEXT_HOST_USER_ID, MEETING_ID);

        assertThat(response.isKick()).isFalse();
        assertThat(participant.isInMeeting()).isTrue();
        verify(liveKitParticipantManager)
                .disconnectParticipant(MEETING_ID, participantId);
    }

    @Test
    @DisplayName("이미 퇴장한 참여자의 재요청도 멱등 성공하고 LiveKit 토큰을 정리한다")
    void leaveMeeting_succeedsIdempotentlyWhenAlreadyLeft() {
        long participantId = 30L;
        MeetingRoom savedMeetingRoom = createSavedMeetingRoom();
        Member member = createMemberWithId(
                NEXT_HOST_MEMBER_ID,
                NEXT_HOST_USER_ID,
                TEAM_ID,
                "참여자"
        );
        Participant participant = createParticipantWithId(
                participantId,
                NEXT_HOST_MEMBER_ID,
                "BE",
                false
        );

        given(meetingRoomRepository.findActiveById(MEETING_ID))
                .willReturn(Optional.of(savedMeetingRoom));
        given(memberRepository.findByTeamIdAndUserId(
                TEAM_ID,
                NEXT_HOST_USER_ID
        )).willReturn(Optional.of(member));
        given(participantRepository.findByMeetingRoomIdAndMemberId(
                MEETING_ID,
                NEXT_HOST_MEMBER_ID
        )).willReturn(Optional.of(participant));

        ResponseLeaveMeetingDto response =
                meetingService.leaveMeeting(NEXT_HOST_USER_ID, MEETING_ID);

        assertThat(response.isKick()).isFalse();
        assertThat(participant.isInMeeting()).isFalse();
        verify(liveKitParticipantManager)
                .disconnectParticipant(MEETING_ID, participantId);
    }

    @Test
    @DisplayName("현재 호스트는 일반 퇴장할 수 없다")
    void leaveMeeting_rejectsCurrentHost() {
        long participantId = 30L;
        MeetingRoom savedMeetingRoom = createSavedMeetingRoom();
        Member member = createMemberWithId(
                CURRENT_HOST_MEMBER_ID,
                CURRENT_HOST_USER_ID,
                TEAM_ID,
                "호스트"
        );
        Participant participant = createParticipantWithId(
                participantId,
                CURRENT_HOST_MEMBER_ID,
                "BE",
                true
        );

        given(meetingRoomRepository.findActiveById(MEETING_ID))
                .willReturn(Optional.of(savedMeetingRoom));
        given(memberRepository.findByTeamIdAndUserId(
                TEAM_ID,
                CURRENT_HOST_USER_ID
        )).willReturn(Optional.of(member));
        given(participantRepository.findByMeetingRoomIdAndMemberId(
                MEETING_ID,
                CURRENT_HOST_MEMBER_ID
        )).willReturn(Optional.of(participant));

        assertErrorCode(
                () -> meetingService.leaveMeeting(
                        CURRENT_HOST_USER_ID,
                        MEETING_ID
                ),
                ErrorCode.MEETING_HOST_CANNOT_LEAVE
        );
        assertThat(participant.isInMeeting()).isTrue();
        verifyNoInteractions(liveKitParticipantManager);
    }

    @Test
    @DisplayName("활성 회의를 찾을 수 없으면 퇴장을 거부한다")
    void leaveMeeting_rejectsMissingMeeting() {
        given(meetingRoomRepository.findActiveById(MEETING_ID))
                .willReturn(Optional.empty());

        assertErrorCode(
                () -> meetingService.leaveMeeting(
                        CURRENT_HOST_USER_ID,
                        MEETING_ID
                ),
                ErrorCode.MEETING_NOT_FOUND
        );
        verifyNoInteractions(
                memberRepository,
                participantRepository,
                liveKitParticipantManager
        );
    }

    @Test
    @DisplayName("요청자가 회의의 상위 팀 멤버가 아니면 퇴장을 거부한다")
    void leaveMeeting_rejectsRequesterOutsideTeam() {
        MeetingRoom savedMeetingRoom = createSavedMeetingRoom();
        given(meetingRoomRepository.findActiveById(MEETING_ID))
                .willReturn(Optional.of(savedMeetingRoom));
        given(memberRepository.findByTeamIdAndUserId(
                TEAM_ID,
                CURRENT_HOST_USER_ID
        )).willReturn(Optional.empty());

        assertErrorCode(
                () -> meetingService.leaveMeeting(
                        CURRENT_HOST_USER_ID,
                        MEETING_ID
                ),
                ErrorCode.MEETING_ACCESS_DENIED
        );
        verifyNoInteractions(
                participantRepository,
                liveKitParticipantManager
        );
    }

    @Test
    @DisplayName("팀 멤버라도 회의에 초대되지 않았으면 퇴장을 거부한다")
    void leaveMeeting_rejectsUninvitedRequester() {
        MeetingRoom savedMeetingRoom = createSavedMeetingRoom();
        Member member = createMemberWithId(
                CURRENT_HOST_MEMBER_ID,
                CURRENT_HOST_USER_ID,
                TEAM_ID,
                "요청자"
        );

        given(meetingRoomRepository.findActiveById(MEETING_ID))
                .willReturn(Optional.of(savedMeetingRoom));
        given(memberRepository.findByTeamIdAndUserId(
                TEAM_ID,
                CURRENT_HOST_USER_ID
        )).willReturn(Optional.of(member));
        given(participantRepository.findByMeetingRoomIdAndMemberId(
                MEETING_ID,
                CURRENT_HOST_MEMBER_ID
        )).willReturn(Optional.empty());

        assertErrorCode(
                () -> meetingService.leaveMeeting(
                        CURRENT_HOST_USER_ID,
                        MEETING_ID
                ),
                ErrorCode.MEETING_ACCESS_DENIED
        );
        verifyNoInteractions(liveKitParticipantManager);
    }

    @Test
    @DisplayName("LiveKit 연결 종료에 실패하면 입장 상태를 변경하지 않는다")
    void leaveMeeting_keepsPresenceWhenLiveKitDisconnectFails() {
        long participantId = 30L;
        MeetingRoom savedMeetingRoom = createSavedMeetingRoom();
        Member member = createMemberWithId(
                NEXT_HOST_MEMBER_ID,
                NEXT_HOST_USER_ID,
                TEAM_ID,
                "참여자"
        );
        Participant participant = createParticipantWithId(
                participantId,
                NEXT_HOST_MEMBER_ID,
                "BE",
                true
        );

        given(meetingRoomRepository.findActiveById(MEETING_ID))
                .willReturn(Optional.of(savedMeetingRoom));
        given(memberRepository.findByTeamIdAndUserId(
                TEAM_ID,
                NEXT_HOST_USER_ID
        )).willReturn(Optional.of(member));
        given(participantRepository.findByMeetingRoomIdAndMemberId(
                MEETING_ID,
                NEXT_HOST_MEMBER_ID
        )).willReturn(Optional.of(participant));
        willThrow(new CustomException(
                ErrorCode.MEETING_LIVEKIT_DISCONNECT_FAILED
        )).given(liveKitParticipantManager)
                .disconnectParticipant(MEETING_ID, participantId);

        assertErrorCode(
                () -> meetingService.leaveMeeting(
                        NEXT_HOST_USER_ID,
                        MEETING_ID
                ),
                ErrorCode.MEETING_LIVEKIT_DISCONNECT_FAILED
        );
        assertThat(participant.isInMeeting()).isTrue();
    }

    @Test
    @DisplayName("호스트가 참여자를 강퇴하면 LiveKit 연결과 Participant 권한을 제거한다")
    void kickParticipant_disconnectsAndDeletesParticipant() {
        Participant participant = createParticipantWithId(
                NEXT_HOST_PARTICIPANT_ID,
                NEXT_HOST_MEMBER_ID,
                "BE",
                true
        );
        Member participantMember = createMemberWithId(
                NEXT_HOST_MEMBER_ID,
                NEXT_HOST_USER_ID,
                TEAM_ID,
                "참여자"
        );
        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.of(createSavedMeetingRoom()));
        given(participantRepository.findByIdAndMeetingRoomId(
                NEXT_HOST_PARTICIPANT_ID,
                MEETING_ID
        )).willReturn(Optional.of(participant));
        given(memberRepository.findById(NEXT_HOST_MEMBER_ID))
                .willReturn(Optional.of(participantMember));

        ResponseLeaveMeetingDto response = meetingService.kickParticipant(
                CURRENT_HOST_USER_ID,
                MEETING_ID,
                NEXT_HOST_PARTICIPANT_ID
        );

        assertThat(response.isKick()).isTrue();
        verify(liveKitParticipantManager).disconnectParticipant(
                MEETING_ID,
                NEXT_HOST_PARTICIPANT_ID
        );
        verify(participantRepository).delete(participant);
    }

    @Test
    @DisplayName("활성 회의가 없으면 참여자를 강퇴할 수 없다")
    void kickParticipant_rejectsMissingMeeting() {
        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.empty());

        assertErrorCode(
                () -> meetingService.kickParticipant(
                        CURRENT_HOST_USER_ID,
                        MEETING_ID,
                        NEXT_HOST_PARTICIPANT_ID
                ),
                ErrorCode.MEETING_NOT_FOUND
        );
        verifyNoInteractions(
                participantRepository,
                memberRepository,
                liveKitParticipantManager
        );
    }

    @Test
    @DisplayName("호스트가 아닌 사용자는 참여자를 강퇴할 수 없다")
    void kickParticipant_rejectsNonHostRequester() {
        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.of(createSavedMeetingRoom()));

        assertErrorCode(
                () -> meetingService.kickParticipant(
                        NEXT_HOST_USER_ID,
                        MEETING_ID,
                        NEXT_HOST_PARTICIPANT_ID
                ),
                ErrorCode.MEETING_HOST_REQUIRED
        );
        verifyNoInteractions(
                participantRepository,
                memberRepository,
                liveKitParticipantManager
        );
    }

    @Test
    @DisplayName("다른 회의이거나 존재하지 않는 Participant는 강퇴할 수 없다")
    void kickParticipant_rejectsMissingTargetParticipant() {
        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.of(createSavedMeetingRoom()));
        given(participantRepository.findByIdAndMeetingRoomId(
                NEXT_HOST_PARTICIPANT_ID,
                MEETING_ID
        )).willReturn(Optional.empty());

        assertErrorCode(
                () -> meetingService.kickParticipant(
                        CURRENT_HOST_USER_ID,
                        MEETING_ID,
                        NEXT_HOST_PARTICIPANT_ID
                ),
                ErrorCode.MEETING_PARTICIPANT_NOT_FOUND
        );
        verifyNoInteractions(memberRepository, liveKitParticipantManager);
        verify(participantRepository, never()).delete(any(Participant.class));
    }

    @Test
    @DisplayName("회의 호스트 자신은 강퇴할 수 없다")
    void kickParticipant_rejectsCurrentHostTarget() {
        Participant hostParticipant = createParticipantWithId(
                NEXT_HOST_PARTICIPANT_ID,
                CURRENT_HOST_MEMBER_ID,
                "BE",
                true
        );
        Member hostMember = createMemberWithId(
                CURRENT_HOST_MEMBER_ID,
                CURRENT_HOST_USER_ID,
                TEAM_ID,
                "호스트"
        );
        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.of(createSavedMeetingRoom()));
        given(participantRepository.findByIdAndMeetingRoomId(
                NEXT_HOST_PARTICIPANT_ID,
                MEETING_ID
        )).willReturn(Optional.of(hostParticipant));
        given(memberRepository.findById(CURRENT_HOST_MEMBER_ID))
                .willReturn(Optional.of(hostMember));

        assertErrorCode(
                () -> meetingService.kickParticipant(
                        CURRENT_HOST_USER_ID,
                        MEETING_ID,
                        NEXT_HOST_PARTICIPANT_ID
                ),
                ErrorCode.MEETING_HOST_CANNOT_BE_KICKED
        );
        verifyNoInteractions(liveKitParticipantManager);
        verify(participantRepository, never()).delete(any(Participant.class));
    }

    @Test
    @DisplayName("회의 상위 팀과 다른 Member의 Participant는 강퇴할 수 없다")
    void kickParticipant_rejectsTargetOutsideMeetingTeam() {
        long otherTeamId = 999L;
        Participant participant = createParticipantWithId(
                NEXT_HOST_PARTICIPANT_ID,
                NEXT_HOST_MEMBER_ID,
                "BE",
                true
        );
        Member otherTeamMember = createMemberWithId(
                NEXT_HOST_MEMBER_ID,
                NEXT_HOST_USER_ID,
                otherTeamId,
                "다른 팀 참여자"
        );
        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.of(createSavedMeetingRoom()));
        given(participantRepository.findByIdAndMeetingRoomId(
                NEXT_HOST_PARTICIPANT_ID,
                MEETING_ID
        )).willReturn(Optional.of(participant));
        given(memberRepository.findById(NEXT_HOST_MEMBER_ID))
                .willReturn(Optional.of(otherTeamMember));

        assertErrorCode(
                () -> meetingService.kickParticipant(
                        CURRENT_HOST_USER_ID,
                        MEETING_ID,
                        NEXT_HOST_PARTICIPANT_ID
                ),
                ErrorCode.MEETING_PARTICIPANT_NOT_FOUND
        );
        verifyNoInteractions(liveKitParticipantManager);
        verify(participantRepository, never()).delete(any(Participant.class));
    }

    @Test
    @DisplayName("LiveKit 연결 종료가 실패하면 Participant를 삭제하지 않는다")
    void kickParticipant_keepsParticipantWhenLiveKitDisconnectFails() {
        Participant participant = createParticipantWithId(
                NEXT_HOST_PARTICIPANT_ID,
                NEXT_HOST_MEMBER_ID,
                "BE",
                true
        );
        Member participantMember = createMemberWithId(
                NEXT_HOST_MEMBER_ID,
                NEXT_HOST_USER_ID,
                TEAM_ID,
                "참여자"
        );
        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.of(createSavedMeetingRoom()));
        given(participantRepository.findByIdAndMeetingRoomId(
                NEXT_HOST_PARTICIPANT_ID,
                MEETING_ID
        )).willReturn(Optional.of(participant));
        given(memberRepository.findById(NEXT_HOST_MEMBER_ID))
                .willReturn(Optional.of(participantMember));
        willThrow(new CustomException(
                ErrorCode.MEETING_LIVEKIT_DISCONNECT_FAILED
        )).given(liveKitParticipantManager).disconnectParticipant(
                MEETING_ID,
                NEXT_HOST_PARTICIPANT_ID
        );

        assertErrorCode(
                () -> meetingService.kickParticipant(
                        CURRENT_HOST_USER_ID,
                        MEETING_ID,
                        NEXT_HOST_PARTICIPANT_ID
                ),
                ErrorCode.MEETING_LIVEKIT_DISCONNECT_FAILED
        );
        verify(participantRepository, never()).delete(any(Participant.class));
    }

    @Test
    @DisplayName("호스트가 회의를 종료하면 상태를 정리하고 AI 종료 이벤트를 발행한다")
    void endMeeting_endsLiveKitRoomAndSoftDeletesMeeting() {
        MeetingRoom savedMeetingRoom = createSavedMeetingRoom();
        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.of(savedMeetingRoom));

        meetingService.endMeeting(CURRENT_HOST_USER_ID, MEETING_ID);

        assertThat(savedMeetingRoom.isDeleted()).isTrue();
        assertThat(savedMeetingRoom.getDeletedAt()).isNotNull();
        verify(liveKitRoomManager).endRoom(MEETING_ID);
        verify(participantRepository).leaveAllByMeetingRoomId(MEETING_ID);

        ArgumentCaptor<MeetingTranscriptionEndedEvent> eventCaptor =
                ArgumentCaptor.forClass(
                        MeetingTranscriptionEndedEvent.class
                );
        verify(applicationEventPublisher)
                .publishEvent(eventCaptor.capture());

        MeetingTranscriptionEndedEvent event = eventCaptor.getValue();
        assertThat(event.meetingId()).isEqualTo(MEETING_ID);
        assertThat(event.endedAt()).isEqualTo(
                savedMeetingRoom.getDeletedAt()
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        );
    }

    @Test
    @DisplayName("호스트가 아닌 사용자는 회의를 종료할 수 없다")
    void endMeeting_rejectsNonHost() {
        MeetingRoom savedMeetingRoom = createSavedMeetingRoom();
        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.of(savedMeetingRoom));

        assertErrorCode(
                () -> meetingService.endMeeting(
                        NEXT_HOST_USER_ID,
                        MEETING_ID
                ),
                ErrorCode.MEETING_HOST_REQUIRED
        );

        assertThat(savedMeetingRoom.isDeleted()).isFalse();
        verifyNoInteractions(liveKitRoomManager, participantRepository);
        verify(applicationEventPublisher, never())
                .publishEvent(any(MeetingTranscriptionEndedEvent.class));
    }

    @Test
    @DisplayName("활성 회의를 찾을 수 없으면 종료를 거부한다")
    void endMeeting_rejectsMissingMeeting() {
        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.empty());

        assertErrorCode(
                () -> meetingService.endMeeting(
                        CURRENT_HOST_USER_ID,
                        MEETING_ID
                ),
                ErrorCode.MEETING_NOT_FOUND
        );

        verifyNoInteractions(liveKitRoomManager, participantRepository);
        verify(applicationEventPublisher, never())
                .publishEvent(any(MeetingTranscriptionEndedEvent.class));
    }

    @Test
    @DisplayName("LiveKit 방 종료에 실패하면 회의와 참여자 상태를 변경하지 않는다")
    void endMeeting_keepsStateWhenLiveKitEndFails() {
        MeetingRoom savedMeetingRoom = createSavedMeetingRoom();
        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.of(savedMeetingRoom));
        willThrow(new CustomException(
                ErrorCode.MEETING_LIVEKIT_ROOM_END_FAILED
        )).given(liveKitRoomManager).endRoom(MEETING_ID);

        assertErrorCode(
                () -> meetingService.endMeeting(
                        CURRENT_HOST_USER_ID,
                        MEETING_ID
                ),
                ErrorCode.MEETING_LIVEKIT_ROOM_END_FAILED
        );

        assertThat(savedMeetingRoom.isDeleted()).isFalse();
        verify(participantRepository, never())
                .leaveAllByMeetingRoomId(MEETING_ID);
        verify(applicationEventPublisher, never())
                .publishEvent(any(MeetingTranscriptionEndedEvent.class));
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

    @Test
    @DisplayName("초대된 사용자가 현재 입장 중인 참여자 목록을 조회한다")
    void getParticipants_returnsCurrentParticipants() {
        long requesterMemberId = 11L;
        long attendeeMemberId = 20L;
        long requesterParticipantId = 30L;
        long attendeeParticipantId = 31L;

        Member requesterMember =
                createMemberWithId(requesterMemberId, CURRENT_HOST_USER_ID, TEAM_ID, "호스트");
        Member attendeeMember =
                createMemberWithId(attendeeMemberId, NEXT_HOST_USER_ID, TEAM_ID, "참여자");
        Participant requesterParticipant =
                createParticipantWithId(requesterParticipantId, requesterMemberId, "BE", true);
        Participant attendeeParticipant =
                createParticipantWithId(attendeeParticipantId, attendeeMemberId, "FE", true);
        User requesterUser =
                createUserWithId(CURRENT_HOST_USER_ID, "https://example.com/host.png");
        User attendeeUser =
                createUserWithId(NEXT_HOST_USER_ID, "https://example.com/attendee.png");

        given(meetingRoomRepository.findActiveById(MEETING_ID))
                .willReturn(Optional.of(meetingRoom));
        given(memberRepository.findByTeamIdAndUserId(TEAM_ID, CURRENT_HOST_USER_ID))
                .willReturn(Optional.of(requesterMember));
        given(participantRepository.findByMeetingRoomIdAndMemberId(
                MEETING_ID,
                requesterMemberId
        )).willReturn(Optional.of(requesterParticipant));
        given(participantRepository
                .findAllByMeetingRoomIdAndIsInMeetingTrueOrderByIdAsc(MEETING_ID))
                .willReturn(List.of(requesterParticipant, attendeeParticipant));
        given(memberRepository.findAllById(any()))
                .willReturn(List.of(requesterMember, attendeeMember));
        given(userRepository.findAllById(any()))
                .willReturn(List.of(requesterUser, attendeeUser));

        List<ResponseMeetingParticipantDto> response =
                meetingService.getParticipants(CURRENT_HOST_USER_ID, MEETING_ID);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).participantId()).isEqualTo(requesterParticipantId);
        assertThat(response.get(0).nickname()).isEqualTo("호스트");
        assertThat(response.get(0).participantRole()).isEqualTo("BE");
        assertThat(response.get(0).isHost()).isTrue();
        assertThat(response.get(0).isInMeeting()).isTrue();
        assertThat(response.get(1).participantId()).isEqualTo(attendeeParticipantId);
        assertThat(response.get(1).nickname()).isEqualTo("참여자");
        assertThat(response.get(1).participantRole()).isEqualTo("FE");
        assertThat(response.get(1).isHost()).isFalse();
        assertThat(response.get(1).isInMeeting()).isTrue();
    }

    @Test
    @DisplayName("삭제되지 않은 회의를 찾을 수 없으면 참여자 조회를 거부한다")
    void getParticipants_rejectsMissingMeeting() {
        given(meetingRoomRepository.findActiveById(MEETING_ID))
                .willReturn(Optional.empty());

        assertErrorCode(
                () -> meetingService.getParticipants(CURRENT_HOST_USER_ID, MEETING_ID),
                ErrorCode.MEETING_NOT_FOUND
        );
        verifyNoInteractions(participantRepository, memberRepository, userRepository);
    }

    @Test
    @DisplayName("요청자가 회의의 상위 팀 멤버가 아니면 참여자 조회를 거부한다")
    void getParticipants_rejectsRequesterOutsideTeam() {
        given(meetingRoomRepository.findActiveById(MEETING_ID))
                .willReturn(Optional.of(meetingRoom));
        given(memberRepository.findByTeamIdAndUserId(TEAM_ID, CURRENT_HOST_USER_ID))
                .willReturn(Optional.empty());

        assertErrorCode(
                () -> meetingService.getParticipants(CURRENT_HOST_USER_ID, MEETING_ID),
                ErrorCode.MEETING_ACCESS_DENIED
        );
        verifyNoInteractions(participantRepository, userRepository);
    }

    @Test
    @DisplayName("팀 멤버라도 회의에 초대되지 않았으면 참여자 조회를 거부한다")
    void getParticipants_rejectsUninvitedRequester() {
        long requesterMemberId = 11L;
        Member requesterMember =
                createMemberWithId(requesterMemberId, CURRENT_HOST_USER_ID, TEAM_ID, "요청자");
        given(meetingRoomRepository.findActiveById(MEETING_ID))
                .willReturn(Optional.of(meetingRoom));
        given(memberRepository.findByTeamIdAndUserId(TEAM_ID, CURRENT_HOST_USER_ID))
                .willReturn(Optional.of(requesterMember));
        given(participantRepository.findByMeetingRoomIdAndMemberId(
                MEETING_ID,
                requesterMemberId
        )).willReturn(Optional.empty());

        assertErrorCode(
                () -> meetingService.getParticipants(CURRENT_HOST_USER_ID, MEETING_ID),
                ErrorCode.MEETING_ACCESS_DENIED
        );
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("호스트는 검색어에 맞는 초대 후보 목록을 조회한다")
    void getInviteCandidates_returnsMappedCandidatesForHost() {
        Member candidateMember =
                createMemberWithId(20L, NEXT_HOST_USER_ID, TEAM_ID, "BackendDev");
        User candidateUser = createUserWithId(
                NEXT_HOST_USER_ID,
                "https://example.com/backend.png"
        );
        ResponseMeetingInviteCandidateDto expectedResponse =
                new ResponseMeetingInviteCandidateDto(
                        candidateMember.getId(),
                        candidateUser.getId(),
                        candidateMember.getNickname(),
                        candidateUser.getEmail(),
                        candidateUser.getProfileImageUrl()
                );

        given(meetingRoomRepository.findActiveById(MEETING_ID))
                .willReturn(Optional.of(meetingRoom));
        given(memberRepository.findMeetingInviteCandidates(
                TEAM_ID,
                MEETING_ID,
                "backend"
        )).willReturn(List.<Object[]>of(new Object[]{candidateMember, candidateUser}));
        given(meetingMapper.toInviteCandidate(candidateMember, candidateUser))
                .willReturn(expectedResponse);

        List<ResponseMeetingInviteCandidateDto> response =
                meetingService.getInviteCandidates(
                        CURRENT_HOST_USER_ID,
                        MEETING_ID,
                        "  backend  "
                );

        assertThat(response).containsExactly(expectedResponse);
        verify(memberRepository).findMeetingInviteCandidates(
                TEAM_ID,
                MEETING_ID,
                "backend"
        );
        verify(meetingMapper).toInviteCandidate(candidateMember, candidateUser);
    }

    @Test
    @DisplayName("초대 후보 검색어가 공백이면 전체 조회용 빈 문자열로 정규화한다")
    void getInviteCandidates_normalizesBlankQuery() {
        given(meetingRoomRepository.findActiveById(MEETING_ID))
                .willReturn(Optional.of(meetingRoom));
        given(memberRepository.findMeetingInviteCandidates(TEAM_ID, MEETING_ID, ""))
                .willReturn(List.of());

        List<ResponseMeetingInviteCandidateDto> response =
                meetingService.getInviteCandidates(
                        CURRENT_HOST_USER_ID,
                        MEETING_ID,
                        "   "
                );

        assertThat(response).isEmpty();
        verify(memberRepository).findMeetingInviteCandidates(TEAM_ID, MEETING_ID, "");
    }

    @Test
    @DisplayName("초대 가능한 멤버가 없으면 빈 목록을 반환한다")
    void getInviteCandidates_returnsEmptyListWhenNoCandidateExists() {
        given(meetingRoomRepository.findActiveById(MEETING_ID))
                .willReturn(Optional.of(meetingRoom));
        given(memberRepository.findMeetingInviteCandidates(
                TEAM_ID,
                MEETING_ID,
                "nobody"
        )).willReturn(List.of());

        List<ResponseMeetingInviteCandidateDto> response =
                meetingService.getInviteCandidates(
                        CURRENT_HOST_USER_ID,
                        MEETING_ID,
                        "nobody"
                );

        assertThat(response).isEmpty();
        verifyNoInteractions(meetingMapper);
    }

    @Test
    @DisplayName("초대 후보 검색 시 회의가 없으면 MEETING_NOT_FOUND 예외가 발생한다")
    void getInviteCandidates_rejectsMissingMeeting() {
        given(meetingRoomRepository.findActiveById(MEETING_ID))
                .willReturn(Optional.empty());

        assertErrorCode(
                () -> meetingService.getInviteCandidates(
                        CURRENT_HOST_USER_ID,
                        MEETING_ID,
                        ""
                ),
                ErrorCode.MEETING_NOT_FOUND
        );
        verifyNoInteractions(memberRepository, meetingMapper);
    }

    @Test
    @DisplayName("호스트가 아니면 초대 후보를 검색할 수 없다")
    void getInviteCandidates_rejectsNonHostRequester() {
        given(meetingRoomRepository.findActiveById(MEETING_ID))
                .willReturn(Optional.of(meetingRoom));

        assertErrorCode(
                () -> meetingService.getInviteCandidates(
                        NEXT_HOST_USER_ID,
                        MEETING_ID,
                        ""
                ),
                ErrorCode.MEETING_HOST_REQUIRED
        );
        verifyNoInteractions(memberRepository, meetingMapper);
    }

    @Test
    @DisplayName("호스트는 같은 팀의 활성 사용자를 미접속 Participant로 초대한다")
    void inviteMember_createsDisconnectedParticipant() {
        Long participantId = 40L;
        Member inviteeMember = createMemberWithId(
                NEXT_HOST_MEMBER_ID,
                NEXT_HOST_USER_ID,
                TEAM_ID,
                "초대 대상"
        );
        User inviteeUser = createUserWithId(NEXT_HOST_USER_ID, null);
        Participant savedParticipant = createParticipantWithId(
                participantId,
                NEXT_HOST_MEMBER_ID,
                "BE",
                false
        );
        ResponseMeetingInvitationDto expectedResponse =
                new ResponseMeetingInvitationDto(
                        participantId,
                        MEETING_ID,
                        NEXT_HOST_MEMBER_ID,
                        NEXT_HOST_USER_ID,
                        "BE",
                        false
                );

        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.of(meetingRoom));
        given(memberRepository.findByTeamIdAndUserId(TEAM_ID, NEXT_HOST_USER_ID))
                .willReturn(Optional.of(inviteeMember));
        given(userRepository.findById(NEXT_HOST_USER_ID))
                .willReturn(Optional.of(inviteeUser));
        given(participantRepository.existsByMeetingRoomIdAndMemberId(
                MEETING_ID,
                NEXT_HOST_MEMBER_ID
        )).willReturn(false);
        given(memberRepository.findTeamRoleNameByMemberId(NEXT_HOST_MEMBER_ID))
                .willReturn(Optional.of("BE"));
        given(participantRepository.save(any(Participant.class)))
                .willReturn(savedParticipant);
        given(meetingMapper.toInvitationResponse(savedParticipant, inviteeMember))
                .willReturn(expectedResponse);

        ResponseMeetingInvitationDto response = meetingService.inviteMember(
                CURRENT_HOST_USER_ID,
                MEETING_ID,
                NEXT_HOST_USER_ID
        );

        ArgumentCaptor<Participant> participantCaptor =
                ArgumentCaptor.forClass(Participant.class);
        verify(participantRepository).save(participantCaptor.capture());

        Participant createdParticipant = participantCaptor.getValue();
        assertThat(createdParticipant.getMeetingRoomId()).isEqualTo(MEETING_ID);
        assertThat(createdParticipant.getMemberId()).isEqualTo(NEXT_HOST_MEMBER_ID);
        assertThat(createdParticipant.getParticipantRole()).isEqualTo("BE");
        assertThat(createdParticipant.isInMeeting()).isFalse();
        assertThat(response).isEqualTo(expectedResponse);
    }

    @Test
    @DisplayName("팀 역할이 없는 멤버는 Participant 역할을 null로 저장한다")
    void inviteMember_savesNullRoleWhenInviteeHasNoTeamRole() {
        Member inviteeMember = createMemberWithId(
                NEXT_HOST_MEMBER_ID,
                NEXT_HOST_USER_ID,
                TEAM_ID,
                "초대 대상"
        );
        User inviteeUser = createUserWithId(NEXT_HOST_USER_ID, null);
        Participant savedParticipant = createParticipantWithId(
                40L,
                NEXT_HOST_MEMBER_ID,
                null,
                false
        );
        ResponseMeetingInvitationDto expectedResponse =
                new ResponseMeetingInvitationDto(
                        40L,
                        MEETING_ID,
                        NEXT_HOST_MEMBER_ID,
                        NEXT_HOST_USER_ID,
                        null,
                        false
                );

        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.of(meetingRoom));
        given(memberRepository.findByTeamIdAndUserId(TEAM_ID, NEXT_HOST_USER_ID))
                .willReturn(Optional.of(inviteeMember));
        given(userRepository.findById(NEXT_HOST_USER_ID))
                .willReturn(Optional.of(inviteeUser));
        given(participantRepository.existsByMeetingRoomIdAndMemberId(
                MEETING_ID,
                NEXT_HOST_MEMBER_ID
        )).willReturn(false);
        given(memberRepository.findTeamRoleNameByMemberId(NEXT_HOST_MEMBER_ID))
                .willReturn(Optional.empty());
        given(participantRepository.save(any(Participant.class)))
                .willReturn(savedParticipant);
        given(meetingMapper.toInvitationResponse(savedParticipant, inviteeMember))
                .willReturn(expectedResponse);

        meetingService.inviteMember(
                CURRENT_HOST_USER_ID,
                MEETING_ID,
                NEXT_HOST_USER_ID
        );

        ArgumentCaptor<Participant> participantCaptor =
                ArgumentCaptor.forClass(Participant.class);
        verify(participantRepository).save(participantCaptor.capture());
        assertThat(participantCaptor.getValue().getParticipantRole()).isNull();
    }

    @Test
    @DisplayName("회의가 없으면 멤버를 초대할 수 없다")
    void inviteMember_rejectsMissingMeeting() {
        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.empty());

        assertErrorCode(
                () -> meetingService.inviteMember(
                        CURRENT_HOST_USER_ID,
                        MEETING_ID,
                        NEXT_HOST_USER_ID
                ),
                ErrorCode.MEETING_NOT_FOUND
        );
        verifyNoInteractions(
                memberRepository,
                userRepository,
                participantRepository,
                meetingMapper
        );
    }

    @Test
    @DisplayName("호스트가 아닌 사용자는 멤버를 초대할 수 없다")
    void inviteMember_rejectsNonHostRequester() {
        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.of(meetingRoom));

        assertErrorCode(
                () -> meetingService.inviteMember(
                        NEXT_HOST_USER_ID,
                        MEETING_ID,
                        CURRENT_HOST_USER_ID
                ),
                ErrorCode.MEETING_HOST_REQUIRED
        );
        verifyNoInteractions(
                memberRepository,
                userRepository,
                participantRepository,
                meetingMapper
        );
    }

    @Test
    @DisplayName("회의 상위 팀의 멤버가 아닌 사용자는 초대할 수 없다")
    void inviteMember_rejectsUserOutsideMeetingTeam() {
        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.of(meetingRoom));
        given(memberRepository.findByTeamIdAndUserId(TEAM_ID, NEXT_HOST_USER_ID))
                .willReturn(Optional.empty());

        assertErrorCode(
                () -> meetingService.inviteMember(
                        CURRENT_HOST_USER_ID,
                        MEETING_ID,
                        NEXT_HOST_USER_ID
                ),
                ErrorCode.SPACE_MEMBER_NOT_FOUND
        );
        verifyNoInteractions(userRepository, participantRepository, meetingMapper);
    }

    @Test
    @DisplayName("사용자 정보가 없으면 회의에 초대할 수 없다")
    void inviteMember_rejectsMissingUser() {
        Member inviteeMember = createMemberWithId(
                NEXT_HOST_MEMBER_ID,
                NEXT_HOST_USER_ID,
                TEAM_ID,
                "초대 대상"
        );
        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.of(meetingRoom));
        given(memberRepository.findByTeamIdAndUserId(TEAM_ID, NEXT_HOST_USER_ID))
                .willReturn(Optional.of(inviteeMember));
        given(userRepository.findById(NEXT_HOST_USER_ID))
                .willReturn(Optional.empty());

        assertErrorCode(
                () -> meetingService.inviteMember(
                        CURRENT_HOST_USER_ID,
                        MEETING_ID,
                        NEXT_HOST_USER_ID
                ),
                ErrorCode.USER_NOT_FOUND
        );
        verifyNoInteractions(participantRepository, meetingMapper);
    }

    @Test
    @DisplayName("탈퇴한 사용자는 회의에 초대할 수 없다")
    void inviteMember_rejectsDeletedUser() {
        Member inviteeMember = createMemberWithId(
                NEXT_HOST_MEMBER_ID,
                NEXT_HOST_USER_ID,
                TEAM_ID,
                "초대 대상"
        );
        User deletedUser = createUserWithId(NEXT_HOST_USER_ID, null);
        deletedUser.withdraw();

        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.of(meetingRoom));
        given(memberRepository.findByTeamIdAndUserId(TEAM_ID, NEXT_HOST_USER_ID))
                .willReturn(Optional.of(inviteeMember));
        given(userRepository.findById(NEXT_HOST_USER_ID))
                .willReturn(Optional.of(deletedUser));

        assertErrorCode(
                () -> meetingService.inviteMember(
                        CURRENT_HOST_USER_ID,
                        MEETING_ID,
                        NEXT_HOST_USER_ID
                ),
                ErrorCode.USER_NOT_FOUND
        );
        verifyNoInteractions(participantRepository, meetingMapper);
    }

    @Test
    @DisplayName("이미 Participant인 멤버는 중복 초대할 수 없다")
    void inviteMember_rejectsAlreadyInvitedMember() {
        Member inviteeMember = createMemberWithId(
                NEXT_HOST_MEMBER_ID,
                NEXT_HOST_USER_ID,
                TEAM_ID,
                "초대 대상"
        );
        User inviteeUser = createUserWithId(NEXT_HOST_USER_ID, null);

        given(meetingRoomRepository.findActiveByIdForUpdate(MEETING_ID))
                .willReturn(Optional.of(meetingRoom));
        given(memberRepository.findByTeamIdAndUserId(TEAM_ID, NEXT_HOST_USER_ID))
                .willReturn(Optional.of(inviteeMember));
        given(userRepository.findById(NEXT_HOST_USER_ID))
                .willReturn(Optional.of(inviteeUser));
        given(participantRepository.existsByMeetingRoomIdAndMemberId(
                MEETING_ID,
                NEXT_HOST_MEMBER_ID
        )).willReturn(true);

        assertErrorCode(
                () -> meetingService.inviteMember(
                        CURRENT_HOST_USER_ID,
                        MEETING_ID,
                        NEXT_HOST_USER_ID
                ),
                ErrorCode.MEETING_ALREADY_INVITED
        );
        verify(memberRepository, never())
                .findTeamRoleNameByMemberId(NEXT_HOST_MEMBER_ID);
        verify(participantRepository, never()).save(any(Participant.class));
        verifyNoInteractions(meetingMapper);
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

    private MeetingRoom createSavedMeetingRoom() {
        return createSavedMeetingRoom(
                MEETING_ID,
                CURRENT_HOST_USER_ID,
                "데일리 미팅",
                CREATED_AT
        );
    }

    private MeetingRoom createSavedMeetingRoom(
            Long meetingId,
            Long hostUserId,
            String meetingRoomName,
            OffsetDateTime createdAt
    ) {
        MeetingRoom savedMeetingRoom = MeetingRoom.builder()
                .teamId(TEAM_ID)
                .hostId(hostUserId)
                .name(meetingRoomName)
                .build();
        ReflectionTestUtils.setField(savedMeetingRoom, "id", meetingId);
        ReflectionTestUtils.setField(savedMeetingRoom, "createdAt", createdAt);
        return savedMeetingRoom;
    }

    private ResponseCreateMeetingDto createCreateMeetingResponse() {
        return new ResponseCreateMeetingDto(
                MEETING_ID,
                TEAM_ID,
                new ResponseMeetingHostDto(CURRENT_HOST_USER_ID),
                CREATED_AT,
                1
        );
    }

    private Team createTeam() {
        Team team = Team.builder()
                .name("프로젝트 팀")
                .ownerId(CURRENT_HOST_USER_ID)
                .build();
        ReflectionTestUtils.setField(team, "id", TEAM_ID);
        return team;
    }

    private Member createMemberWithTeamRoleId(
            Long memberId,
            Long userId,
            Long teamId,
            Long teamRoleId
    ) {
        return createMemberWithAuthority(
                memberId,
                userId,
                teamId,
                MemberAuthority.MEMBER,
                teamRoleId
        );
    }

    private Member createMemberWithAuthority(
            Long memberId,
            Long userId,
            Long teamId,
            MemberAuthority authority,
            Long teamRoleId
    ) {
        Member member = Member.builder()
                .userId(userId)
                .teamId(teamId)
                .authority(authority)
                .teamRoleId(teamRoleId)
                .nickname("참여자")
                .build();
        ReflectionTestUtils.setField(member, "id", memberId);
        return member;
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

    private Member createMemberWithId(
            Long memberId,
            Long userId,
            Long teamId,
            String nickname
    ) {
        Member member = Member.builder()
                .userId(userId)
                .teamId(teamId)
                .authority(MemberAuthority.MEMBER)
                .nickname(nickname)
                .build();
        ReflectionTestUtils.setField(member, "id", memberId);
        return member;
    }

    private Participant createParticipantWithId(
            Long participantId,
            Long memberId,
            String participantRole,
            boolean isInMeeting
    ) {
        return createParticipantForMeeting(
                participantId,
                MEETING_ID,
                memberId,
                participantRole,
                isInMeeting
        );
    }

    private Participant createParticipantForMeeting(
            Long participantId,
            Long meetingId,
            Long memberId,
            boolean isInMeeting
    ) {
        return createParticipantForMeeting(
                participantId,
                meetingId,
                memberId,
                null,
                isInMeeting
        );
    }

    private Participant createParticipantForMeeting(
            Long participantId,
            Long meetingId,
            Long memberId,
            String participantRole,
            boolean isInMeeting
    ) {
        Participant participant = Participant.builder()
                .meetingRoomId(meetingId)
                .memberId(memberId)
                .participantRole(participantRole)
                .isInMeeting(isInMeeting)
                .build();
        ReflectionTestUtils.setField(participant, "id", participantId);
        return participant;
    }

    private User createUserWithId(Long userId, String profileImageUrl) {
        User user = User.builder()
                .email("user" + userId + "@example.com")
                .password("encoded-password")
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        ReflectionTestUtils.setField(user, "profileImageUrl", profileImageUrl);
        return user;
    }
}

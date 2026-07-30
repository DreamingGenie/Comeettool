package com.ssafy.backend.meeting.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.meeting.dto.RequestCreateMeetingDto;
import com.ssafy.backend.meeting.dto.RequestTransferHostDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingParticipantDto;
import com.ssafy.backend.meeting.dto.ResponseCreateMeetingDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingHostDto;
import com.ssafy.backend.meeting.dto.ResponseTransferHostDto;
import com.ssafy.backend.meeting.entity.MeetingRoom;
import com.ssafy.backend.meeting.entity.Participant;
import com.ssafy.backend.meeting.mapper.MeetingMapper;
import com.ssafy.backend.meeting.repository.MeetingRoomRepository;
import com.ssafy.backend.meeting.repository.ParticipantRepository;
import com.ssafy.backend.user.entity.User;
import com.ssafy.backend.user.repository.UserRepository;
import com.ssafy.backend.member.entity.Member;
import com.ssafy.backend.member.entity.MemberAuthority;
import com.ssafy.backend.member.repository.MemberRepository;
import com.ssafy.backend.space.entity.Team;
import com.ssafy.backend.space.repository.TeamRepository;

/**
 * MEET-06 호스트 양도 및 MEET-07 참여자 조회 서비스 단위 테스트.
 * 저장소는 Mock으로 분리하고 호스트 양도, 조회 접근 권한, 현재 참여자 응답 조립을 검증한다.
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
        Member hostMember = createMemberWithId(
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
        assertThat(response).isEqualTo(expectedResponse);
    }

    @Test
    @DisplayName("팀 역할이 없는 Host는 역할을 null로 저장한다")
    void addMeeting_savesNullParticipantRoleWhenHostHasNoTeamRole() {
        Team team = createTeam();
        Member hostMember = createMemberWithId(
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
        Member hostMember = createMemberWithId(
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
        MeetingRoom savedMeetingRoom = createMeetingRoom(CURRENT_HOST_USER_ID);
        ReflectionTestUtils.setField(savedMeetingRoom, "id", MEETING_ID);
        ReflectionTestUtils.setField(savedMeetingRoom, "createdAt", CREATED_AT);
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

    private Member createMemberWithId(
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
        Participant participant = Participant.builder()
                .meetingRoomId(MEETING_ID)
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

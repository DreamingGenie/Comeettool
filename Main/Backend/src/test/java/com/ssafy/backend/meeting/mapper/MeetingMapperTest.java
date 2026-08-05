package com.ssafy.backend.meeting.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ssafy.backend.meeting.dto.ResponseCreateMeetingDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingInviteCandidateDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingInvitationDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingListDto;
import com.ssafy.backend.meeting.entity.MeetingRoom;
import com.ssafy.backend.meeting.entity.Participant;
import com.ssafy.backend.member.entity.Member;
import com.ssafy.backend.member.entity.MemberAuthority;
import com.ssafy.backend.user.entity.User;

/**
 * MeetingMapper 단위 테스트.
 * MeetingRoom·Member·User 엔티티가
 * MEET-01 생성, MEET-02 목록, MEET-09 초대 후보,
 * MEET-10 초대 응답으로 정확히 변환되는지 검증한다.
 */
@DisplayName("회의 Mapper 테스트")
class MeetingMapperTest {

    private static final Long MEETING_ID = 100L;
    private static final Long TEAM_ID = 10L;
    private static final Long HOST_USER_ID = 1L;
    private static final OffsetDateTime CREATED_AT =
            OffsetDateTime.parse("2026-07-30T12:00:00+09:00");

    private final MeetingMapper meetingMapper = new MeetingMapper();

    @Test
    @DisplayName("회의 생성 응답에 회의·스페이스·호스트·참여자 수를 매핑한다")
    void toCreateResponse_mapsMeetingCreationResult() {
        MeetingRoom meetingRoom = createSavedMeetingRoom();

        ResponseCreateMeetingDto response =
                meetingMapper.toCreateResponse(meetingRoom, HOST_USER_ID, 1);

        assertThat(response.meetingRoomId()).isEqualTo(MEETING_ID);
        assertThat(response.teamId()).isEqualTo(TEAM_ID);
        assertThat(response.host().userId()).isEqualTo(HOST_USER_ID);
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);
        assertThat(response.participantCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("회의 목록 응답에 현재 접속자 수와 내 접속 상태를 매핑한다")
    void toListItem_mapsMeetingListItem() {
        MeetingRoom meetingRoom = createSavedMeetingRoom();

        ResponseMeetingListDto response =
                meetingMapper.toListItem(meetingRoom, 2L, false);

        assertThat(response.meetingRoomId()).isEqualTo(MEETING_ID);
        assertThat(response.teamId()).isEqualTo(TEAM_ID);
        assertThat(response.meetingRoomName()).isEqualTo("데일리 미팅");
        assertThat(response.host().userId()).isEqualTo(HOST_USER_ID);
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);
        assertThat(response.participantCount()).isEqualTo(2L);
        assertThat(response.isInMeeting()).isFalse();
    }

    @Test
    @DisplayName("초대 후보 응답에 멤버와 사용자 정보를 매핑한다")
    void toInviteCandidate_mapsMemberAndUser() {
        Member member = Member.builder()
                .userId(2L)
                .teamId(TEAM_ID)
                .authority(MemberAuthority.MEMBER)
                .nickname("백엔드개발자")
                .build();
        ReflectionTestUtils.setField(member, "id", 20L);

        User user = User.builder()
                .email("backend@example.com")
                .password("encoded-password")
                .build();
        ReflectionTestUtils.setField(user, "id", 2L);
        ReflectionTestUtils.setField(
                user,
                "profileImageUrl",
                "https://example.com/backend.png"
        );

        ResponseMeetingInviteCandidateDto response =
                meetingMapper.toInviteCandidate(member, user);

        assertThat(response.memberId()).isEqualTo(20L);
        assertThat(response.userId()).isEqualTo(2L);
        assertThat(response.nickname()).isEqualTo("백엔드개발자");
        assertThat(response.email()).isEqualTo("backend@example.com");
        assertThat(response.profileImage())
                .isEqualTo("https://example.com/backend.png");
    }

    @Test
    @DisplayName("회의 초대 응답에 Participant와 Member 정보를 매핑한다")
    void toInvitationResponse_mapsParticipantAndMember() {
        Member member = Member.builder()
                .userId(2L)
                .teamId(TEAM_ID)
                .authority(MemberAuthority.MEMBER)
                .nickname("백엔드개발자")
                .build();
        ReflectionTestUtils.setField(member, "id", 20L);

        Participant participant = Participant.builder()
                .meetingRoomId(MEETING_ID)
                .memberId(member.getId())
                .participantRole("BE")
                .isInMeeting(false)
                .build();
        ReflectionTestUtils.setField(participant, "id", 30L);

        ResponseMeetingInvitationDto response =
                meetingMapper.toInvitationResponse(participant, member);

        assertThat(response.participantId()).isEqualTo(30L);
        assertThat(response.meetingRoomId()).isEqualTo(MEETING_ID);
        assertThat(response.memberId()).isEqualTo(20L);
        assertThat(response.userId()).isEqualTo(2L);
        assertThat(response.participantRole()).isEqualTo("BE");
        assertThat(response.isInMeeting()).isFalse();
    }

    private MeetingRoom createSavedMeetingRoom() {
        MeetingRoom meetingRoom = MeetingRoom.builder()
                .teamId(TEAM_ID)
                .hostId(HOST_USER_ID)
                .name("데일리 미팅")
                .build();
        ReflectionTestUtils.setField(meetingRoom, "id", MEETING_ID);
        ReflectionTestUtils.setField(meetingRoom, "createdAt", CREATED_AT);
        return meetingRoom;
    }
}

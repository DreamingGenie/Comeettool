package com.ssafy.backend.meeting.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.util.ReflectionTestUtils;

import com.ssafy.backend.meeting.entity.MeetingRoom;
import com.ssafy.backend.meeting.entity.Participant;
import com.ssafy.backend.member.entity.Member;
import com.ssafy.backend.member.entity.MemberAuthority;
import com.ssafy.backend.member.repository.MemberRepository;
import com.ssafy.backend.space.entity.Team;
import com.ssafy.backend.user.entity.User;

/**
 * MEET-02·05·09 Repository 통합 테스트.
 * memberId 기반 Participant 조회, 회의별 현재 접속자 집계,
 * 같은 스페이스의 삭제되지 않은 회의 필터와 초대 후보 검색을
 * 실제 PostgreSQL에서 검증한다.
 *
 * <p>프로젝트의 다른 Repository 테스트와 동일하게 로컬 프로필의 실제 DB를 사용한다.
 * SQL v1.1.7 스키마가 필요하며 각 테스트 데이터는 트랜잭션 종료 시 롤백된다.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("MEETING Repository 통합 테스트")
class MeetingRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MeetingRoomRepository meetingRoomRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("memberId로 등록된 모든 Participant를 ID 순서대로 조회한다")
    void findAllByMemberId_returnsParticipantsInIdOrder() {
        User user = persistUser();
        Team team = persistTeam(user.getId());
        Member member = persistMember(user.getId(), team.getId());
        MeetingRoom firstMeeting =
                persistMeeting(team.getId(), user.getId(), "첫 번째 회의", false);
        MeetingRoom secondMeeting =
                persistMeeting(team.getId(), user.getId(), "두 번째 회의", false);
        Participant firstParticipant =
                persistParticipant(firstMeeting.getId(), member.getId(), false);
        Participant secondParticipant =
                persistParticipant(secondMeeting.getId(), member.getId(), true);

        List<Participant> participants =
                participantRepository.findAllByMemberIdOrderByIdAsc(member.getId());

        assertThat(participants)
                .extracting(Participant::getId)
                .containsExactly(firstParticipant.getId(), secondParticipant.getId());
    }

    @Test
    @DisplayName("회의별 isInMeeting=true 참여자 수만 집계한다")
    void countInMeetingParticipants_countsConnectedParticipantsByMeeting() {
        User firstUser = persistUser();
        User secondUser = persistUser();
        Team team = persistTeam(firstUser.getId());
        Member firstMember = persistMember(firstUser.getId(), team.getId());
        Member secondMember = persistMember(secondUser.getId(), team.getId());
        MeetingRoom connectedMeeting =
                persistMeeting(team.getId(), firstUser.getId(), "접속 중 회의", false);
        MeetingRoom disconnectedMeeting =
                persistMeeting(team.getId(), firstUser.getId(), "미접속 회의", false);
        persistParticipant(connectedMeeting.getId(), firstMember.getId(), true);
        persistParticipant(connectedMeeting.getId(), secondMember.getId(), true);
        persistParticipant(disconnectedMeeting.getId(), firstMember.getId(), false);

        List<Object[]> rows = participantRepository
                .countInMeetingParticipantsByMeetingRoomIds(
                        List.of(connectedMeeting.getId(), disconnectedMeeting.getId())
                );

        Map<Long, Long> countByMeetingRoomId = new HashMap<>();
        for (Object[] row : rows) {
            countByMeetingRoomId.put((Long) row[0], (Long) row[1]);
        }
        assertThat(countByMeetingRoomId)
                .containsEntry(connectedMeeting.getId(), 2L)
                .doesNotContainKey(disconnectedMeeting.getId());
    }

    @Test
    @DisplayName("회의 종료 시 해당 회의의 입장 중인 Participant만 모두 퇴장 처리한다")
    void leaveAllByMeetingRoomId_disconnectsOnlyRequestedMeeting() {
        User user = persistUser();
        Team team = persistTeam(user.getId());
        Member member = persistMember(user.getId(), team.getId());
        MeetingRoom requestedMeeting =
                persistMeeting(team.getId(), user.getId(), "종료 회의", false);
        MeetingRoom otherMeeting =
                persistMeeting(team.getId(), user.getId(), "다른 회의", false);
        Participant connectedParticipant = persistParticipant(
                requestedMeeting.getId(),
                member.getId(),
                true
        );
        Participant alreadyLeftParticipant = persistParticipant(
                requestedMeeting.getId(),
                persistMember(persistUser().getId(), team.getId()).getId(),
                false
        );
        Participant otherMeetingParticipant = persistParticipant(
                otherMeeting.getId(),
                member.getId(),
                true
        );
        OffsetDateTime endedAt =
                OffsetDateTime.parse("2026-07-31T17:00:00+09:00");

        requestedMeeting.endMeeting(endedAt);
        int updatedCount = participantRepository
                .leaveAllByMeetingRoomId(requestedMeeting.getId());

        assertThat(updatedCount).isEqualTo(1);
        MeetingRoom endedMeeting = meetingRoomRepository
                .findById(requestedMeeting.getId())
                .orElseThrow();
        assertThat(endedMeeting.isDeleted()).isTrue();
        assertThat(endedMeeting.getDeletedAt()).isEqualTo(endedAt);
        assertThat(participantRepository.findById(connectedParticipant.getId()))
                .get()
                .extracting(Participant::isInMeeting)
                .isEqualTo(false);
        assertThat(participantRepository.findById(alreadyLeftParticipant.getId()))
                .get()
                .extracting(Participant::isInMeeting)
                .isEqualTo(false);
        assertThat(participantRepository.findById(otherMeetingParticipant.getId()))
                .get()
                .extracting(Participant::isInMeeting)
                .isEqualTo(true);
    }

    @Test
    @DisplayName("같은 스페이스의 삭제되지 않은 회의만 최신순으로 반환한다")
    void findAllActiveByIdsAndTeamId_filtersTeamAndSoftDeletedMeeting() {
        User user = persistUser();
        Team requestedTeam = persistTeam(user.getId());
        Team otherTeam = persistTeam(user.getId());
        MeetingRoom olderActiveMeeting =
                persistMeeting(requestedTeam.getId(), user.getId(), "이전 회의", false);
        MeetingRoom newerActiveMeeting =
                persistMeeting(requestedTeam.getId(), user.getId(), "최근 회의", false);
        MeetingRoom deletedMeeting =
                persistMeeting(requestedTeam.getId(), user.getId(), "삭제 회의", true);
        MeetingRoom otherTeamMeeting =
                persistMeeting(otherTeam.getId(), user.getId(), "다른 팀 회의", false);

        List<MeetingRoom> meetings = meetingRoomRepository.findAllActiveByIdsAndTeamId(
                List.of(
                        olderActiveMeeting.getId(),
                        newerActiveMeeting.getId(),
                        deletedMeeting.getId(),
                        otherTeamMeeting.getId()
                ),
                requestedTeam.getId()
        );

        assertThat(meetings)
                .extracting(MeetingRoom::getId)
                .containsExactly(newerActiveMeeting.getId(), olderActiveMeeting.getId());
    }

    @Test
    @DisplayName("초대 후보 전체 조회는 같은 팀의 현재 회의 미초대 활성 사용자만 반환한다")
    void findMeetingInviteCandidates_returnsOnlyEligibleTeamMembers() {
        User hostUser = persistUser();
        User candidateUser = persistUser();
        User otherMeetingUser = persistUser();
        User deletedUser = persistUser("deleted-" + token() + "@test.com", true);
        User otherTeamUser = persistUser();
        Team requestedTeam = persistTeam(hostUser.getId());
        Team otherTeam = persistTeam(otherTeamUser.getId());

        Member hostMember = persistMember(hostUser.getId(), requestedTeam.getId());
        Member candidateMember =
                persistMember(candidateUser.getId(), requestedTeam.getId());
        Member otherMeetingMember =
                persistMember(otherMeetingUser.getId(), requestedTeam.getId());
        persistMember(deletedUser.getId(), requestedTeam.getId());
        persistMember(otherTeamUser.getId(), otherTeam.getId());

        MeetingRoom requestedMeeting = persistMeeting(
                requestedTeam.getId(),
                hostUser.getId(),
                "현재 회의",
                false
        );
        MeetingRoom otherMeeting = persistMeeting(
                requestedTeam.getId(),
                hostUser.getId(),
                "다른 회의",
                false
        );
        persistParticipant(requestedMeeting.getId(), hostMember.getId(), false);
        persistParticipant(otherMeeting.getId(), otherMeetingMember.getId(), false);

        List<Object[]> rows = memberRepository.findMeetingInviteCandidates(
                requestedTeam.getId(),
                requestedMeeting.getId(),
                ""
        );

        assertThat(rows)
                .extracting(row -> ((Member) row[0]).getId())
                .containsExactly(candidateMember.getId(), otherMeetingMember.getId());
    }

    @Test
    @DisplayName("초대 후보는 멤버 닉네임을 대소문자 구분 없이 부분 검색한다")
    void findMeetingInviteCandidates_searchesNicknameIgnoringCase() {
        User hostUser = persistUser();
        User backendUser = persistUser();
        User frontendUser = persistUser();
        Team team = persistTeam(hostUser.getId());
        Member backendMember =
                persistMember(backendUser.getId(), team.getId(), "BackendDev");
        persistMember(frontendUser.getId(), team.getId(), "FrontendDev");
        MeetingRoom meeting =
                persistMeeting(team.getId(), hostUser.getId(), "검색 회의", false);

        List<Object[]> rows = memberRepository.findMeetingInviteCandidates(
                team.getId(),
                meeting.getId(),
                "BACKEND"
        );

        assertThat(rows)
                .extracting(row -> ((Member) row[0]).getId())
                .containsExactly(backendMember.getId());
    }

    @Test
    @DisplayName("초대 후보는 사용자 이메일을 대소문자 구분 없이 부분 검색한다")
    void findMeetingInviteCandidates_searchesEmailIgnoringCase() {
        User hostUser = persistUser();
        User matchedUser =
                persistUser("Meeting.Member-" + token() + "@Test.com", false);
        User unmatchedUser =
                persistUser("other-" + token() + "@test.com", false);
        Team team = persistTeam(hostUser.getId());
        Member matchedMember =
                persistMember(matchedUser.getId(), team.getId());
        persistMember(unmatchedUser.getId(), team.getId());
        MeetingRoom meeting =
                persistMeeting(team.getId(), hostUser.getId(), "검색 회의", false);

        List<Object[]> rows = memberRepository.findMeetingInviteCandidates(
                team.getId(),
                meeting.getId(),
                "MEETING.MEMBER"
        );

        assertThat(rows)
                .extracting(row -> ((Member) row[0]).getId())
                .containsExactly(matchedMember.getId());
    }

    @Test
    @DisplayName("닉네임과 이메일이 모두 일치하지 않으면 초대 후보 빈 목록을 반환한다")
    void findMeetingInviteCandidates_returnsEmptyListWhenNoMemberMatches() {
        User hostUser = persistUser();
        User candidateUser = persistUser();
        Team team = persistTeam(hostUser.getId());
        persistMember(candidateUser.getId(), team.getId(), "BackendDev");
        MeetingRoom meeting =
                persistMeeting(team.getId(), hostUser.getId(), "검색 회의", false);

        List<Object[]> rows = memberRepository.findMeetingInviteCandidates(
                team.getId(),
                meeting.getId(),
                "no-such-member"
        );

        assertThat(rows).isEmpty();
    }

    private String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private User persistUser() {
        String value = token();
        return persistUser("meeting-" + value + "@test.com", false);
    }

    private User persistUser(String email, boolean deleted) {
        User user = User.builder()
                .email(email)
                .password("$2a$10$encoded")
                .build();
        if (deleted) {
            user.withdraw();
        }
        return entityManager.persistAndFlush(user);
    }

    private Team persistTeam(Long ownerUserId) {
        Team team = Team.builder()
                .name("회의 테스트 팀-" + token())
                .ownerId(ownerUserId)
                .color("#123456")
                .build();
        return entityManager.persistAndFlush(team);
    }

    private Member persistMember(Long userId, Long teamId) {
        return persistMember(userId, teamId, "회의 테스트 멤버-" + token());
    }

    private Member persistMember(Long userId, Long teamId, String nickname) {
        Member member = Member.builder()
                .userId(userId)
                .teamId(teamId)
                .authority(MemberAuthority.MEMBER)
                .nickname(nickname)
                .build();
        return entityManager.persistAndFlush(member);
    }

    private MeetingRoom persistMeeting(
            Long teamId,
            Long hostUserId,
            String name,
            boolean deleted
    ) {
        MeetingRoom meetingRoom = MeetingRoom.builder()
                .teamId(teamId)
                .hostId(hostUserId)
                .name(name + "-" + token())
                .build();
        if (deleted) {
            ReflectionTestUtils.setField(meetingRoom, "isDeleted", true);
            ReflectionTestUtils.setField(meetingRoom, "deletedAt", OffsetDateTime.now());
        }
        return entityManager.persistAndFlush(meetingRoom);
    }

    private Participant persistParticipant(
            Long meetingRoomId,
            Long memberId,
            boolean isInMeeting
    ) {
        Participant participant = Participant.builder()
                .meetingRoomId(meetingRoomId)
                .memberId(memberId)
                .participantRole(null)
                .isInMeeting(isInMeeting)
                .build();
        return entityManager.persistAndFlush(participant);
    }
}

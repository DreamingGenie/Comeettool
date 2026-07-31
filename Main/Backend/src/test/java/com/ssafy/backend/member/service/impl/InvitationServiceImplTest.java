package com.ssafy.backend.member.service.impl;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.member.entity.Invitation;
import com.ssafy.backend.member.entity.Member;
import com.ssafy.backend.member.entity.MemberAuthority;
import com.ssafy.backend.member.dto.RequestInviteMemberDto;
import com.ssafy.backend.member.dto.ResponseInviteMemberDto;
import com.ssafy.backend.member.dto.ResponseMyInvitationDto;
import com.ssafy.backend.member.repository.InvitationRepository;
import com.ssafy.backend.member.repository.MemberRepository;
import com.ssafy.backend.space.entity.Team;
import com.ssafy.backend.space.repository.TeamRepository;
import com.ssafy.backend.user.entity.User;
import com.ssafy.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * InvitationServiceImpl 단위 테스트. Repository는 전부 Mock — 비즈니스 로직·저장 데이터 형태만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InvitationServiceImpl 단위 테스트")
class InvitationServiceImplTest {

    private static final Long INVITER_ID = 1L;
    private static final Long TEAM_ID = 10L;
    private static final Long TARGET_USER_ID = 2L;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private InvitationRepository invitationRepository;

    @InjectMocks
    private InvitationServiceImpl invitationService;

    private Team teamWithId(Long id, Long ownerId) {
        Team team = Team.builder().name("팀A").description("설명").ownerId(ownerId).color("#123456").build();
        ReflectionTestUtils.setField(team, "id", id);
        return team;
    }

    private Team teamNamed(Long id, String name) {
        Team team = Team.builder().name(name).description("설명").ownerId(1L).color("#123456").build();
        ReflectionTestUtils.setField(team, "id", id);
        return team;
    }

    private User userWithNickname(Long id, String nickname, boolean deleted) {
        User user = User.builder().email("u" + id + "@test.com").password("enc").build();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "nickname", nickname);
        if (deleted) {
            user.withdraw();
        }
        return user;
    }

    private Invitation invitationOf(Long teamId, Long inviterId, Long targetUserId, OffsetDateTime createdAt) {
        Invitation invitation = Invitation.builder()
                .invitationId(UUID.randomUUID())
                .teamId(teamId)
                .inviterId(inviterId)
                .targetUserId(targetUserId)
                .expiresAt(createdAt.plusDays(1))
                .build();
        ReflectionTestUtils.setField(invitation, "createdAt", createdAt);
        return invitation;
    }

    @Nested
    @DisplayName("MEMBER-02 멤버 초대")
    class InviteMember {

        @Test
        @DisplayName("정상 초대 시 invitationId를 반환하고 team/inviter/target/expiresAt이 반영된 Invitation을 저장한다")
        void inviteMember_savesInvitationAndReturnsId() {
            // given
            Team team = teamWithId(TEAM_ID, INVITER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(TEAM_ID)).willReturn(Optional.of(team));
            given(userRepository.existsById(TARGET_USER_ID)).willReturn(true);
            given(memberRepository.existsByTeamIdAndUserId(TEAM_ID, TARGET_USER_ID)).willReturn(false);
            given(invitationRepository.existsByTeamIdAndTargetUserIdAndExpiresAtAfter(
                    eq(TEAM_ID), eq(TARGET_USER_ID), any(OffsetDateTime.class))).willReturn(false);
            given(invitationRepository.saveAndFlush(any(Invitation.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            OffsetDateTime beforeCall = OffsetDateTime.now();

            // when
            ResponseInviteMemberDto response = invitationService.inviteMember(
                    INVITER_ID, TEAM_ID, new RequestInviteMemberDto(TARGET_USER_ID));

            // then
            assertThat(response.invitationId()).isNotBlank();

            ArgumentCaptor<Invitation> captor = ArgumentCaptor.forClass(Invitation.class);
            verify(invitationRepository).saveAndFlush(captor.capture());
            Invitation saved = captor.getValue();
            assertThat(saved.getInvitationId().toString()).isEqualTo(response.invitationId());
            assertThat(saved.getTeamId()).isEqualTo(TEAM_ID);
            assertThat(saved.getInviterId()).isEqualTo(INVITER_ID);
            assertThat(saved.getTargetUserId()).isEqualTo(TARGET_USER_ID);
            // TTL 1일 정책 — expiresAt은 호출 시점 + 1일 근방(오차 허용 2초)이어야 한다.
            assertThat(saved.getExpiresAt())
                    .isCloseTo(beforeCall.plusDays(1), within(2, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("존재하지 않거나 삭제된 스페이스면 SPACE_NOT_FOUND 예외가 발생한다")
        void inviteMember_throwsNotFoundWhenSpaceAbsent() {
            given(teamRepository.findByIdAndIsDeletedFalse(TEAM_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> invitationService.inviteMember(
                    INVITER_ID, TEAM_ID, new RequestInviteMemberDto(TARGET_USER_ID)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_NOT_FOUND);
            verifyNoInteractions(invitationRepository);
        }

        @Test
        @DisplayName("요청자가 소유자가 아니면 SPACE_OWNER_ONLY 예외가 발생한다")
        void inviteMember_throwsForNonOwner() {
            Team team = teamWithId(TEAM_ID, 99L);
            given(teamRepository.findByIdAndIsDeletedFalse(TEAM_ID)).willReturn(Optional.of(team));

            assertThatThrownBy(() -> invitationService.inviteMember(
                    INVITER_ID, TEAM_ID, new RequestInviteMemberDto(TARGET_USER_ID)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_OWNER_ONLY);
            verifyNoInteractions(invitationRepository);
        }

        @Test
        @DisplayName("대상 유저가 존재하지 않으면 USER_NOT_FOUND 예외가 발생한다")
        void inviteMember_throwsWhenTargetUserNotFound() {
            Team team = teamWithId(TEAM_ID, INVITER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(TEAM_ID)).willReturn(Optional.of(team));
            given(userRepository.existsById(TARGET_USER_ID)).willReturn(false);

            assertThatThrownBy(() -> invitationService.inviteMember(
                    INVITER_ID, TEAM_ID, new RequestInviteMemberDto(TARGET_USER_ID)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
            verifyNoInteractions(invitationRepository);
        }

        @Test
        @DisplayName("이미 멤버인 유저를 초대하면 MEMBER_ALREADY_JOINED 예외가 발생하고 저장하지 않는다")
        void inviteMember_throwsWhenAlreadyMember() {
            Team team = teamWithId(TEAM_ID, INVITER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(TEAM_ID)).willReturn(Optional.of(team));
            given(userRepository.existsById(TARGET_USER_ID)).willReturn(true);
            given(memberRepository.existsByTeamIdAndUserId(TEAM_ID, TARGET_USER_ID)).willReturn(true);

            assertThatThrownBy(() -> invitationService.inviteMember(
                    INVITER_ID, TEAM_ID, new RequestInviteMemberDto(TARGET_USER_ID)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MEMBER_ALREADY_JOINED);
            verifyNoInteractions(invitationRepository);
        }

        @Test
        @DisplayName("이미 대기 중인 초대가 있으면 INVITATION_ALREADY_PENDING 예외가 발생하고 저장하지 않는다")
        void inviteMember_throwsWhenInvitationAlreadyPending() {
            Team team = teamWithId(TEAM_ID, INVITER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(TEAM_ID)).willReturn(Optional.of(team));
            given(userRepository.existsById(TARGET_USER_ID)).willReturn(true);
            given(memberRepository.existsByTeamIdAndUserId(TEAM_ID, TARGET_USER_ID)).willReturn(false);
            given(invitationRepository.existsByTeamIdAndTargetUserIdAndExpiresAtAfter(
                    eq(TEAM_ID), eq(TARGET_USER_ID), any(OffsetDateTime.class))).willReturn(true);

            assertThatThrownBy(() -> invitationService.inviteMember(
                    INVITER_ID, TEAM_ID, new RequestInviteMemberDto(TARGET_USER_ID)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.INVITATION_ALREADY_PENDING);
            verify(invitationRepository, never()).saveAndFlush(any(Invitation.class));
        }

        @Test
        @DisplayName("동시 요청으로 유니크 제약(team_id, target_user_id)을 위반하면 INVITATION_ALREADY_PENDING으로 변환된다")
        void inviteMember_translatesUniqueConstraintViolationToPending() {
            Team team = teamWithId(TEAM_ID, INVITER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(TEAM_ID)).willReturn(Optional.of(team));
            given(userRepository.existsById(TARGET_USER_ID)).willReturn(true);
            given(memberRepository.existsByTeamIdAndUserId(TEAM_ID, TARGET_USER_ID)).willReturn(false);
            // 애플리케이션 레벨 체크 시점엔 대기 중인 초대가 없었지만(false), 동시 요청이 먼저 커밋되어 DB 유니크 제약에 걸리는 상황을 재현한다.
            given(invitationRepository.existsByTeamIdAndTargetUserIdAndExpiresAtAfter(
                    eq(TEAM_ID), eq(TARGET_USER_ID), any(OffsetDateTime.class))).willReturn(false);
            given(invitationRepository.saveAndFlush(any(Invitation.class)))
                    .willThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

            assertThatThrownBy(() -> invitationService.inviteMember(
                    INVITER_ID, TEAM_ID, new RequestInviteMemberDto(TARGET_USER_ID)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.INVITATION_ALREADY_PENDING);
        }
    }

    @Nested
    @DisplayName("MEMBER-03 내가 받은 초대 목록 조회")
    class FindMyInvitations {

        private static final long ME_USER_ID = 5L;

        @Test
        @DisplayName("초대가 있으면 스페이스명·초대자 닉네임을 채워 리포지토리가 반환한 순서 그대로 매핑한다")
        void findMyInvitations_returnsMappedListInRepositoryOrder() {
            // given — 리포지토리가 이미 최신순으로 정렬해 반환한다고 가정(정렬 자체는 InvitationRepositoryTest에서 실 DB로 검증).
            OffsetDateTime now = OffsetDateTime.now();
            Invitation newer = invitationOf(10L, 1L, ME_USER_ID, now);
            Invitation older = invitationOf(20L, 2L, ME_USER_ID, now.minusHours(1));
            given(invitationRepository.findByTargetUserIdAndExpiresAtAfterOrderByCreatedAtDesc(
                    eq(ME_USER_ID), any(OffsetDateTime.class)))
                    .willReturn(List.of(newer, older));
            given(teamRepository.findById(10L)).willReturn(Optional.of(teamNamed(10L, "코밋툴")));
            given(teamRepository.findById(20L)).willReturn(Optional.of(teamNamed(20L, "두번째스페이스")));
            given(userRepository.findById(1L)).willReturn(Optional.of(userWithNickname(1L, "asd", false)));
            given(userRepository.findById(2L)).willReturn(Optional.of(userWithNickname(2L, "초대자2", false)));

            // when
            List<ResponseMyInvitationDto> result = invitationService.findMyInvitations(ME_USER_ID);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).invitationId()).isEqualTo(newer.getInvitationId().toString());
            assertThat(result.get(0).spaceId()).isEqualTo(10L);
            assertThat(result.get(0).spaceName()).isEqualTo("코밋툴");
            assertThat(result.get(0).inviterNickname()).isEqualTo("asd");
            assertThat(result.get(0).createdAt()).isEqualTo(now);
            assertThat(result.get(1).invitationId()).isEqualTo(older.getInvitationId().toString());
            assertThat(result.get(1).spaceName()).isEqualTo("두번째스페이스");
            assertThat(result.get(1).inviterNickname()).isEqualTo("초대자2");
        }

        @Test
        @DisplayName("받은 초대가 없으면 빈 리스트를 반환한다")
        void findMyInvitations_returnsEmptyListWhenNoneReceived() {
            given(invitationRepository.findByTargetUserIdAndExpiresAtAfterOrderByCreatedAtDesc(
                    eq(ME_USER_ID), any(OffsetDateTime.class)))
                    .willReturn(List.of());

            List<ResponseMyInvitationDto> result = invitationService.findMyInvitations(ME_USER_ID);

            assertThat(result).isEmpty();
            verifyNoInteractions(teamRepository);
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("초대자가 탈퇴했으면 닉네임 대신 fallback 값을 사용한다")
        void findMyInvitations_usesFallbackNicknameWhenInviterWithdrawn() {
            OffsetDateTime now = OffsetDateTime.now();
            Invitation invitation = invitationOf(10L, 1L, ME_USER_ID, now);
            given(invitationRepository.findByTargetUserIdAndExpiresAtAfterOrderByCreatedAtDesc(
                    eq(ME_USER_ID), any(OffsetDateTime.class)))
                    .willReturn(List.of(invitation));
            given(teamRepository.findById(10L)).willReturn(Optional.of(teamNamed(10L, "코밋툴")));
            given(userRepository.findById(1L)).willReturn(Optional.of(userWithNickname(1L, "탈퇴자", true)));

            List<ResponseMyInvitationDto> result = invitationService.findMyInvitations(ME_USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).inviterNickname()).isEqualTo("(알 수 없음)");
        }

        @Test
        @DisplayName("초대자를 찾을 수 없으면 닉네임 대신 fallback 값을 사용한다")
        void findMyInvitations_usesFallbackNicknameWhenInviterNotFound() {
            OffsetDateTime now = OffsetDateTime.now();
            Invitation invitation = invitationOf(10L, 1L, ME_USER_ID, now);
            given(invitationRepository.findByTargetUserIdAndExpiresAtAfterOrderByCreatedAtDesc(
                    eq(ME_USER_ID), any(OffsetDateTime.class)))
                    .willReturn(List.of(invitation));
            given(teamRepository.findById(10L)).willReturn(Optional.of(teamNamed(10L, "코밋툴")));
            given(userRepository.findById(1L)).willReturn(Optional.empty());

            List<ResponseMyInvitationDto> result = invitationService.findMyInvitations(ME_USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).inviterNickname()).isEqualTo("(알 수 없음)");
        }

        @Test
        @DisplayName("스페이스가 존재하지 않는 초대는 결과에서 제외된다(방어적 처리, 예외 없음)")
        void findMyInvitations_skipsInvitationWhenTeamNotFound() {
            OffsetDateTime now = OffsetDateTime.now();
            Invitation withMissingTeam = invitationOf(10L, 1L, ME_USER_ID, now);
            Invitation withTeam = invitationOf(20L, 2L, ME_USER_ID, now.minusHours(1));
            given(invitationRepository.findByTargetUserIdAndExpiresAtAfterOrderByCreatedAtDesc(
                    eq(ME_USER_ID), any(OffsetDateTime.class)))
                    .willReturn(List.of(withMissingTeam, withTeam));
            given(teamRepository.findById(10L)).willReturn(Optional.empty());
            given(teamRepository.findById(20L)).willReturn(Optional.of(teamNamed(20L, "두번째스페이스")));
            given(userRepository.findById(2L)).willReturn(Optional.of(userWithNickname(2L, "초대자2", false)));

            List<ResponseMyInvitationDto> result = invitationService.findMyInvitations(ME_USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).spaceId()).isEqualTo(20L);
            verify(userRepository, never()).findById(1L);
        }
    }

    @Nested
    @DisplayName("MEMBER-03 초대 수락")
    class AcceptInvitation {

        private static final long ACCEPTOR_ID = 5L;

        @Test
        @DisplayName("정상 수락 시 MEMBER 권한 멤버로 등록되고 초대는 삭제된다")
        void acceptInvitation_registersMemberAndDeletesInvitation() {
            Invitation invitation = invitationOf(TEAM_ID, INVITER_ID, ACCEPTOR_ID, OffsetDateTime.now());
            given(invitationRepository.findByInvitationIdAndExpiresAtAfter(
                    eq(invitation.getInvitationId()), any(OffsetDateTime.class)))
                    .willReturn(Optional.of(invitation));
            given(memberRepository.existsByTeamIdAndUserId(TEAM_ID, ACCEPTOR_ID)).willReturn(false);
            given(userRepository.findById(ACCEPTOR_ID))
                    .willReturn(Optional.of(userWithNickname(ACCEPTOR_ID, "수락자", false)));

            // when
            invitationService.acceptInvitation(ACCEPTOR_ID, invitation.getInvitationId().toString());

            // then
            ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
            verify(memberRepository).save(memberCaptor.capture());
            Member saved = memberCaptor.getValue();
            assertThat(saved.getUserId()).isEqualTo(ACCEPTOR_ID);
            assertThat(saved.getTeamId()).isEqualTo(TEAM_ID);
            assertThat(saved.getAuthority()).isEqualTo(MemberAuthority.MEMBER);
            assertThat(saved.getNickname()).isEqualTo("수락자");
            verify(invitationRepository).delete(invitation);
        }

        @Test
        @DisplayName("존재하지 않거나 만료된 초대면 INVITATION_NOT_FOUND 예외가 발생하고 부수효과가 없다")
        void acceptInvitation_throwsWhenInvitationNotFoundOrExpired() {
            UUID invitationId = UUID.randomUUID();
            given(invitationRepository.findByInvitationIdAndExpiresAtAfter(eq(invitationId), any(OffsetDateTime.class)))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> invitationService.acceptInvitation(ACCEPTOR_ID, invitationId.toString()))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.INVITATION_NOT_FOUND);
            verify(memberRepository, never()).save(any(Member.class));
            verify(invitationRepository, never()).delete(any(Invitation.class));
        }

        @Test
        @DisplayName("본인 초대가 아니면 INVITATION_NOT_FOUND 예외가 발생하고 부수효과가 없다(정보 노출 최소화)")
        void acceptInvitation_throwsWhenNotOwnInvitation() {
            Invitation invitation = invitationOf(TEAM_ID, INVITER_ID, ACCEPTOR_ID, OffsetDateTime.now());
            given(invitationRepository.findByInvitationIdAndExpiresAtAfter(
                    eq(invitation.getInvitationId()), any(OffsetDateTime.class)))
                    .willReturn(Optional.of(invitation));
            long anotherUserId = 999L;

            assertThatThrownBy(
                    () -> invitationService.acceptInvitation(anotherUserId, invitation.getInvitationId().toString()))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.INVITATION_NOT_FOUND);
            verify(memberRepository, never()).save(any(Member.class));
            verify(invitationRepository, never()).delete(any(Invitation.class));
        }

        @Test
        @DisplayName("이미 멤버면 MEMBER_ALREADY_JOINED 예외가 발생한다 — Member는 저장하지 않되, 의미 없어진 초대는 삭제한다")
        void acceptInvitation_throwsWhenAlreadyMemberAndDeletesInvitation() {
            Invitation invitation = invitationOf(TEAM_ID, INVITER_ID, ACCEPTOR_ID, OffsetDateTime.now());
            given(invitationRepository.findByInvitationIdAndExpiresAtAfter(
                    eq(invitation.getInvitationId()), any(OffsetDateTime.class)))
                    .willReturn(Optional.of(invitation));
            given(memberRepository.existsByTeamIdAndUserId(TEAM_ID, ACCEPTOR_ID)).willReturn(true);

            assertThatThrownBy(
                    () -> invitationService.acceptInvitation(ACCEPTOR_ID, invitation.getInvitationId().toString()))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MEMBER_ALREADY_JOINED);
            verify(memberRepository, never()).save(any(Member.class));
            verify(invitationRepository).delete(invitation);
        }
    }
}
package com.ssafy.backend.member.service.impl;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.member.entity.Invitation;
import com.ssafy.backend.member.dto.RequestInviteMemberDto;
import com.ssafy.backend.member.dto.ResponseInviteMemberDto;
import com.ssafy.backend.member.repository.InvitationRepository;
import com.ssafy.backend.member.repository.MemberRepository;
import com.ssafy.backend.space.entity.Team;
import com.ssafy.backend.space.repository.TeamRepository;
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
import java.util.Optional;

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
}
package com.ssafy.backend.member.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.member.dto.InvitationData;
import com.ssafy.backend.member.dto.RequestInviteMemberDto;
import com.ssafy.backend.member.dto.ResponseInviteMemberDto;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * InvitationServiceImpl 단위 테스트. Repository/Redis는 전부 Mock — 비즈니스 로직·Redis 저장 형태만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InvitationServiceImpl 단위 테스트")
class InvitationServiceImplTest {

    private static final Long INVITER_ID = 1L;
    private static final Long TEAM_ID = 10L;
    private static final Long TARGET_USER_ID = 2L;
    private static final Duration TTL = Duration.ofDays(1);

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private InvitationServiceImpl invitationService;

    private Team teamWithId(Long id, Long ownerId) {
        Team team = Team.builder().name("팀A").description("설명").ownerId(ownerId).color("#123456").build();
        ReflectionTestUtils.setField(team, "id", id);
        return team;
    }

    private String lookupKey(Long spaceId, Long targetUserId) {
        return "invitation:lookup:" + spaceId + ":" + targetUserId;
    }

    @Nested
    @DisplayName("MEMBER-02 멤버 초대")
    class InviteMember {

        @Test
        @DisplayName("정상 초대 시 invitationId를 반환하고 Redis에 본 key·lookup key를 TTL 1일로 저장한다")
        void inviteMember_savesInvitationAndReturnsId() throws Exception {
            // given
            Team team = teamWithId(TEAM_ID, INVITER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(TEAM_ID)).willReturn(Optional.of(team));
            given(userRepository.existsById(TARGET_USER_ID)).willReturn(true);
            given(memberRepository.existsByTeamIdAndUserId(TEAM_ID, TARGET_USER_ID)).willReturn(false);
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.setIfAbsent(eq(lookupKey(TEAM_ID, TARGET_USER_ID)), anyString(), eq(TTL)))
                    .willReturn(true);

            // when
            ResponseInviteMemberDto response = invitationService.inviteMember(
                    INVITER_ID, TEAM_ID, new RequestInviteMemberDto(TARGET_USER_ID));

            // then
            assertThat(response.invitationId()).isNotBlank();

            // lookup key는 setIfAbsent(SETNX)로 선점 — 저장된 값(invitationId)이 응답과 같은지 확인.
            ArgumentCaptor<String> lookupValueCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations).setIfAbsent(eq(lookupKey(TEAM_ID, TARGET_USER_ID)), lookupValueCaptor.capture(), eq(TTL));
            assertThat(lookupValueCaptor.getValue()).isEqualTo(response.invitationId());

            // 본 key(invitation:{uuid})는 InvitationData JSON으로 저장.
            ArgumentCaptor<String> dataCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations).set(eq("invitation:" + response.invitationId()), dataCaptor.capture(), eq(TTL));
            InvitationData savedData = objectMapper.readValue(dataCaptor.getValue(), InvitationData.class);
            assertThat(savedData.spaceId()).isEqualTo(TEAM_ID);
            assertThat(savedData.inviterId()).isEqualTo(INVITER_ID);
            assertThat(savedData.targetUserId()).isEqualTo(TARGET_USER_ID);
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
            verifyNoInteractions(redisTemplate);
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
            verifyNoInteractions(redisTemplate);
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
            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("이미 멤버인 유저를 초대하면 MEMBER_ALREADY_JOINED 예외가 발생하고 Redis에 쓰지 않는다")
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
            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("이미 대기 중인 초대가 있으면 INVITATION_ALREADY_PENDING 예외가 발생하고 기존 초대를 덮어쓰지 않는다")
        void inviteMember_throwsWhenInvitationAlreadyPending() {
            Team team = teamWithId(TEAM_ID, INVITER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(TEAM_ID)).willReturn(Optional.of(team));
            given(userRepository.existsById(TARGET_USER_ID)).willReturn(true);
            given(memberRepository.existsByTeamIdAndUserId(TEAM_ID, TARGET_USER_ID)).willReturn(false);
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            // 이미 대기 중 = 다른 요청이 SETNX로 선점 완료 → setIfAbsent가 false를 반환.
            given(valueOperations.setIfAbsent(eq(lookupKey(TEAM_ID, TARGET_USER_ID)), anyString(), eq(TTL)))
                    .willReturn(false);

            assertThatThrownBy(() -> invitationService.inviteMember(
                    INVITER_ID, TEAM_ID, new RequestInviteMemberDto(TARGET_USER_ID)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.INVITATION_ALREADY_PENDING);
            // 기존 초대(invitation:{uuid})를 덮어쓰지 않았는지 확인 — set()은 호출되지 않아야 한다.
            verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        }
    }
}
package com.ssafy.backend.member.service.impl;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.member.dto.RequestAssignTeamRoleDto;
import com.ssafy.backend.member.dto.RequestChangeAuthorityDto;
import com.ssafy.backend.member.dto.RequestCreateTeamRoleDto;
import com.ssafy.backend.member.dto.ResponseAssignTeamRoleDto;
import com.ssafy.backend.member.dto.ResponseChangeAuthorityDto;
import com.ssafy.backend.member.dto.ResponseTeamRoleDto;
import com.ssafy.backend.member.entity.Member;
import com.ssafy.backend.member.entity.MemberAuthority;
import com.ssafy.backend.member.entity.TeamRole;
import com.ssafy.backend.member.repository.MemberRepository;
import com.ssafy.backend.member.repository.TeamRoleRepository;
import com.ssafy.backend.space.entity.Team;
import com.ssafy.backend.space.repository.TeamRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * MemberServiceImpl 단위 테스트. Repository는 전부 Mock — 비즈니스 로직만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemberServiceImpl 단위 테스트")
class MemberServiceImplTest {

    private static final Long OWNER_ID = 1L;
    private static final Long SPACE_ID = 10L;
    private static final Long MEMBER_ID = 100L;
    private static final Long TARGET_USER_ID = 2L;
    private static final Long TEAM_ROLE_ID = 3L;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private TeamRoleRepository teamRoleRepository;

    @InjectMocks
    private MemberServiceImpl memberService;

    private Team teamWithOwner(Long spaceId, Long ownerId) {
        Team team = Team.builder().name("팀A").description("설명").ownerId(ownerId).color("#123456").build();
        ReflectionTestUtils.setField(team, "id", spaceId);
        return team;
    }

    private Member memberOf(Long memberId, Long teamId, Long userId, MemberAuthority authority) {
        Member member = Member.builder()
                .userId(userId)
                .teamId(teamId)
                .authority(authority)
                .nickname("닉네임")
                .build();
        ReflectionTestUtils.setField(member, "id", memberId);
        return member;
    }

    private TeamRole teamRoleOf(Long teamRoleId, Long teamId, String roleName) {
        TeamRole teamRole = TeamRole.builder().teamId(teamId).roleName(roleName).build();
        ReflectionTestUtils.setField(teamRole, "id", teamRoleId);
        return teamRole;
    }

    @Nested
    @DisplayName("MEMBER-04 멤버 강퇴")
    class KickMember {

        @Test
        @DisplayName("정상 강퇴 시 대상 멤버 레코드가 삭제된다")
        void kickMember_deletesTargetMember() {
            Team team = teamWithOwner(SPACE_ID, OWNER_ID);
            Member member = memberOf(MEMBER_ID, SPACE_ID, TARGET_USER_ID, MemberAuthority.MEMBER);
            given(teamRepository.findActiveByIdForUpdate(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

            memberService.kickMember(OWNER_ID, SPACE_ID, MEMBER_ID);

            verify(memberRepository).delete(member);
        }

        @Test
        @DisplayName("존재하지 않거나 삭제된 스페이스면 SPACE_NOT_FOUND 예외가 발생하고 멤버 조회를 시도하지 않는다")
        void kickMember_throwsWhenSpaceNotFound() {
            given(teamRepository.findActiveByIdForUpdate(SPACE_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.kickMember(OWNER_ID, SPACE_ID, MEMBER_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_NOT_FOUND);
            verifyNoInteractions(memberRepository);
        }

        @Test
        @DisplayName("요청자가 소유자가 아니면 SPACE_OWNER_ONLY 예외가 발생하고 멤버 조회를 시도하지 않는다")
        void kickMember_throwsWhenRequesterIsNotOwner() {
            Long nonOwnerId = 99L;
            Team team = teamWithOwner(SPACE_ID, OWNER_ID);
            given(teamRepository.findActiveByIdForUpdate(SPACE_ID)).willReturn(Optional.of(team));

            assertThatThrownBy(() -> memberService.kickMember(nonOwnerId, SPACE_ID, MEMBER_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_OWNER_ONLY);
            verifyNoInteractions(memberRepository);
        }

        @Test
        @DisplayName("memberId가 존재하지 않거나 해당 스페이스 소속이 아니면 SPACE_MEMBER_NOT_FOUND 예외가 발생하고 삭제하지 않는다")
        void kickMember_throwsWhenMemberNotFoundOrBelongsToDifferentSpace() {
            Team team = teamWithOwner(SPACE_ID, OWNER_ID);
            given(teamRepository.findActiveByIdForUpdate(SPACE_ID)).willReturn(Optional.of(team));
            // 다른 spaceId 소속 멤버를 반환해 filter에서 걸러지는 케이스도 포함
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.kickMember(OWNER_ID, SPACE_ID, MEMBER_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_MEMBER_NOT_FOUND);
            verify(memberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("소유자가 자기 자신을 강퇴 시도하면 CANNOT_KICK_SELF 예외가 발생하고 삭제하지 않는다")
        void kickMember_throwsWhenOwnerTriesToKickSelf() {
            Team team = teamWithOwner(SPACE_ID, OWNER_ID);
            // 강퇴 대상 멤버의 userId가 요청자(OWNER_ID)와 동일
            Member selfMember = memberOf(MEMBER_ID, SPACE_ID, OWNER_ID, MemberAuthority.OWNER);
            given(teamRepository.findActiveByIdForUpdate(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(selfMember));

            assertThatThrownBy(() -> memberService.kickMember(OWNER_ID, SPACE_ID, MEMBER_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.CANNOT_KICK_SELF);
            verify(memberRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("MEMBER-07 멤버 권한 변경")
    class ChangeMemberAuthority {

        private RequestChangeAuthorityDto requestOf(String authority) {
            return new RequestChangeAuthorityDto(authority);
        }

        @Test
        @DisplayName("MEMBER를 GUEST로 변경하면 권한이 갱신되고 저장된다")
        void changeMemberAuthority_updatesMemberToGuest() {
            Team team = teamWithOwner(SPACE_ID, OWNER_ID);
            Member member = memberOf(MEMBER_ID, SPACE_ID, TARGET_USER_ID, MemberAuthority.MEMBER);
            given(teamRepository.findActiveByIdForUpdate(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

            ResponseChangeAuthorityDto response =
                    memberService.changeMemberAuthority(OWNER_ID, SPACE_ID, MEMBER_ID, requestOf("GUEST"));

            assertThat(response.memberId()).isEqualTo(MEMBER_ID);
            assertThat(response.authority()).isEqualTo("GUEST");
            assertThat(member.getAuthority()).isEqualTo(MemberAuthority.GUEST);
            verify(memberRepository).save(member);
        }

        @Test
        @DisplayName("GUEST를 MEMBER로 변경하면 권한이 갱신되고 저장된다")
        void changeMemberAuthority_updatesMemberToMember() {
            Team team = teamWithOwner(SPACE_ID, OWNER_ID);
            Member member = memberOf(MEMBER_ID, SPACE_ID, TARGET_USER_ID, MemberAuthority.GUEST);
            given(teamRepository.findActiveByIdForUpdate(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

            ResponseChangeAuthorityDto response =
                    memberService.changeMemberAuthority(OWNER_ID, SPACE_ID, MEMBER_ID, requestOf("MEMBER"));

            assertThat(response.authority()).isEqualTo("MEMBER");
            assertThat(member.getAuthority()).isEqualTo(MemberAuthority.MEMBER);
            verify(memberRepository).save(member);
        }

        @Test
        @DisplayName("이미 같은 권한으로 재요청하면 200으로 응답하되 저장은 호출하지 않는다(멱등)")
        void changeMemberAuthority_isIdempotentWhenAuthorityUnchanged() {
            Team team = teamWithOwner(SPACE_ID, OWNER_ID);
            Member member = memberOf(MEMBER_ID, SPACE_ID, TARGET_USER_ID, MemberAuthority.GUEST);
            given(teamRepository.findActiveByIdForUpdate(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

            ResponseChangeAuthorityDto response =
                    memberService.changeMemberAuthority(OWNER_ID, SPACE_ID, MEMBER_ID, requestOf("GUEST"));

            assertThat(response.authority()).isEqualTo("GUEST");
            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("존재하지 않거나 삭제된 스페이스면 SPACE_NOT_FOUND 예외가 발생하고 멤버 조회를 시도하지 않는다")
        void changeMemberAuthority_throwsWhenSpaceNotFound() {
            given(teamRepository.findActiveByIdForUpdate(SPACE_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    memberService.changeMemberAuthority(OWNER_ID, SPACE_ID, MEMBER_ID, requestOf("GUEST")))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_NOT_FOUND);
            verifyNoInteractions(memberRepository);
        }

        @Test
        @DisplayName("요청자가 소유자가 아니면 SPACE_OWNER_ONLY 예외가 발생하고 멤버 조회를 시도하지 않는다")
        void changeMemberAuthority_throwsWhenRequesterIsNotOwner() {
            Long nonOwnerId = 99L;
            Team team = teamWithOwner(SPACE_ID, OWNER_ID);
            given(teamRepository.findActiveByIdForUpdate(SPACE_ID)).willReturn(Optional.of(team));

            assertThatThrownBy(() ->
                    memberService.changeMemberAuthority(nonOwnerId, SPACE_ID, MEMBER_ID, requestOf("GUEST")))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_OWNER_ONLY);
            verifyNoInteractions(memberRepository);
        }

        @Test
        @DisplayName("authority=OWNER로 요청하면 CANNOT_SET_OWNER_AUTHORITY 예외가 발생하고 멤버 조회를 시도하지 않는다")
        void changeMemberAuthority_throwsWhenRequestedAuthorityIsOwner() {
            Team team = teamWithOwner(SPACE_ID, OWNER_ID);
            given(teamRepository.findActiveByIdForUpdate(SPACE_ID)).willReturn(Optional.of(team));

            assertThatThrownBy(() ->
                    memberService.changeMemberAuthority(OWNER_ID, SPACE_ID, MEMBER_ID, requestOf("OWNER")))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.CANNOT_SET_OWNER_AUTHORITY);
            verifyNoInteractions(memberRepository);
        }

        @Test
        @DisplayName("memberId가 존재하지 않거나 해당 스페이스 소속이 아니면 SPACE_MEMBER_NOT_FOUND 예외가 발생하고 저장하지 않는다")
        void changeMemberAuthority_throwsWhenMemberNotFoundOrBelongsToDifferentSpace() {
            Team team = teamWithOwner(SPACE_ID, OWNER_ID);
            given(teamRepository.findActiveByIdForUpdate(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    memberService.changeMemberAuthority(OWNER_ID, SPACE_ID, MEMBER_ID, requestOf("GUEST")))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_MEMBER_NOT_FOUND);
            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("대상이 오너 본인이면 CANNOT_CHANGE_OWNER_AUTHORITY 예외가 발생하고 저장하지 않는다")
        void changeMemberAuthority_throwsWhenTargetIsOwner() {
            Team team = teamWithOwner(SPACE_ID, OWNER_ID);
            Member ownerMember = memberOf(MEMBER_ID, SPACE_ID, OWNER_ID, MemberAuthority.OWNER);
            given(teamRepository.findActiveByIdForUpdate(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(ownerMember));

            assertThatThrownBy(() ->
                    memberService.changeMemberAuthority(OWNER_ID, SPACE_ID, MEMBER_ID, requestOf("GUEST")))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.CANNOT_CHANGE_OWNER_AUTHORITY);
            verify(memberRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("MEMBER-06 멤버 팀 역할 배정/해제")
    class AssignTeamRole {

        private RequestAssignTeamRoleDto requestOf(Long teamRoleId) {
            return new RequestAssignTeamRoleDto(teamRoleId);
        }

        @Test
        @DisplayName("역할이 없던 멤버에게 배정하면 teamRoleId·roleName이 갱신되고 저장된다")
        void assignTeamRole_assignsRoleAndReturnsRoleName() {
            Team team = teamWithOwner(SPACE_ID, OWNER_ID);
            Member member = memberOf(MEMBER_ID, SPACE_ID, TARGET_USER_ID, MemberAuthority.MEMBER);
            TeamRole teamRole = teamRoleOf(TEAM_ROLE_ID, SPACE_ID, "프론트엔드");
            given(teamRepository.findActiveByIdForUpdate(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
            given(teamRoleRepository.findByIdAndTeamId(TEAM_ROLE_ID, SPACE_ID)).willReturn(Optional.of(teamRole));

            ResponseAssignTeamRoleDto response =
                    memberService.assignTeamRole(OWNER_ID, SPACE_ID, MEMBER_ID, requestOf(TEAM_ROLE_ID));

            assertThat(response.memberId()).isEqualTo(MEMBER_ID);
            assertThat(response.teamRoleId()).isEqualTo(TEAM_ROLE_ID);
            assertThat(response.roleName()).isEqualTo("프론트엔드");
            assertThat(member.getTeamRoleId()).isEqualTo(TEAM_ROLE_ID);
            verify(memberRepository).save(member);
        }

        @Test
        @DisplayName("역할이 있던 멤버를 null로 요청하면 해제되고 teamRoleId·roleName이 null로 응답된다")
        void assignTeamRole_unassignsRole() {
            Team team = teamWithOwner(SPACE_ID, OWNER_ID);
            Member member = memberOf(MEMBER_ID, SPACE_ID, TARGET_USER_ID, MemberAuthority.MEMBER);
            ReflectionTestUtils.setField(member, "teamRoleId", TEAM_ROLE_ID);
            given(teamRepository.findActiveByIdForUpdate(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

            ResponseAssignTeamRoleDto response =
                    memberService.assignTeamRole(OWNER_ID, SPACE_ID, MEMBER_ID, requestOf(null));

            assertThat(response.teamRoleId()).isNull();
            assertThat(response.roleName()).isNull();
            assertThat(member.getTeamRoleId()).isNull();
            verify(memberRepository).save(member);
            verifyNoInteractions(teamRoleRepository);
        }

        @Test
        @DisplayName("이미 미배정 상태에서 null로 재요청하면 200으로 응답하되 저장은 호출하지 않는다(멱등)")
        void assignTeamRole_isIdempotentWhenBothUnassigned() {
            Team team = teamWithOwner(SPACE_ID, OWNER_ID);
            Member member = memberOf(MEMBER_ID, SPACE_ID, TARGET_USER_ID, MemberAuthority.MEMBER);
            given(teamRepository.findActiveByIdForUpdate(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

            ResponseAssignTeamRoleDto response =
                    memberService.assignTeamRole(OWNER_ID, SPACE_ID, MEMBER_ID, requestOf(null));

            assertThat(response.teamRoleId()).isNull();
            verify(memberRepository, never()).save(any());
            verifyNoInteractions(teamRoleRepository);
        }

        @Test
        @DisplayName("이미 같은 역할로 재요청하면 200으로 응답하되 저장은 호출하지 않는다(멱등)")
        void assignTeamRole_isIdempotentWhenSameRoleRequested() {
            Team team = teamWithOwner(SPACE_ID, OWNER_ID);
            Member member = memberOf(MEMBER_ID, SPACE_ID, TARGET_USER_ID, MemberAuthority.MEMBER);
            ReflectionTestUtils.setField(member, "teamRoleId", TEAM_ROLE_ID);
            TeamRole teamRole = teamRoleOf(TEAM_ROLE_ID, SPACE_ID, "프론트엔드");
            given(teamRepository.findActiveByIdForUpdate(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
            given(teamRoleRepository.findByIdAndTeamId(TEAM_ROLE_ID, SPACE_ID)).willReturn(Optional.of(teamRole));

            ResponseAssignTeamRoleDto response =
                    memberService.assignTeamRole(OWNER_ID, SPACE_ID, MEMBER_ID, requestOf(TEAM_ROLE_ID));

            assertThat(response.teamRoleId()).isEqualTo(TEAM_ROLE_ID);
            assertThat(response.roleName()).isEqualTo("프론트엔드");
            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("존재하지 않거나 삭제된 스페이스면 SPACE_NOT_FOUND 예외가 발생하고 멤버 조회를 시도하지 않는다")
        void assignTeamRole_throwsWhenSpaceNotFound() {
            given(teamRepository.findActiveByIdForUpdate(SPACE_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    memberService.assignTeamRole(OWNER_ID, SPACE_ID, MEMBER_ID, requestOf(TEAM_ROLE_ID)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_NOT_FOUND);
            verifyNoInteractions(memberRepository);
            verifyNoInteractions(teamRoleRepository);
        }

        @Test
        @DisplayName("요청자가 소유자가 아니면 SPACE_OWNER_ONLY 예외가 발생하고 멤버 조회를 시도하지 않는다")
        void assignTeamRole_throwsWhenRequesterIsNotOwner() {
            Long nonOwnerId = 99L;
            Team team = teamWithOwner(SPACE_ID, OWNER_ID);
            given(teamRepository.findActiveByIdForUpdate(SPACE_ID)).willReturn(Optional.of(team));

            assertThatThrownBy(() ->
                    memberService.assignTeamRole(nonOwnerId, SPACE_ID, MEMBER_ID, requestOf(TEAM_ROLE_ID)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_OWNER_ONLY);
            verifyNoInteractions(memberRepository);
            verifyNoInteractions(teamRoleRepository);
        }

        @Test
        @DisplayName("memberId가 존재하지 않거나 해당 스페이스 소속이 아니면 SPACE_MEMBER_NOT_FOUND 예외가 발생하고 저장하지 않는다")
        void assignTeamRole_throwsWhenMemberNotFoundOrBelongsToDifferentSpace() {
            Team team = teamWithOwner(SPACE_ID, OWNER_ID);
            given(teamRepository.findActiveByIdForUpdate(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    memberService.assignTeamRole(OWNER_ID, SPACE_ID, MEMBER_ID, requestOf(TEAM_ROLE_ID)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_MEMBER_NOT_FOUND);
            verify(memberRepository, never()).save(any());
            verifyNoInteractions(teamRoleRepository);
        }

        @Test
        @DisplayName("해당 스페이스에 속하지 않는 teamRoleId면 TEAM_ROLE_NOT_FOUND 예외가 발생하고 저장하지 않는다")
        void assignTeamRole_throwsWhenTeamRoleNotFound() {
            Team team = teamWithOwner(SPACE_ID, OWNER_ID);
            Member member = memberOf(MEMBER_ID, SPACE_ID, TARGET_USER_ID, MemberAuthority.MEMBER);
            given(teamRepository.findActiveByIdForUpdate(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
            given(teamRoleRepository.findByIdAndTeamId(TEAM_ROLE_ID, SPACE_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    memberService.assignTeamRole(OWNER_ID, SPACE_ID, MEMBER_ID, requestOf(TEAM_ROLE_ID)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.TEAM_ROLE_NOT_FOUND);
            verify(memberRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("MEMBER-14 팀 역할 생성")
    class CreateTeamRole {

        private static final String ROLE_NAME = "프론트엔드";
        private static final String COLOR = "#3B82F6";

        private RequestCreateTeamRoleDto requestOf(String roleName, String color) {
            return new RequestCreateTeamRoleDto(roleName, color);
        }

        @Test
        @DisplayName("정상 생성 시 team_id/roleName/color가 반영된 TeamRole을 저장하고 응답으로 반환한다")
        void createTeamRole_savesTeamRoleAndReturnsResponse() {
            Team team = teamWithOwner(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(teamRoleRepository.existsByTeamIdAndRoleName(SPACE_ID, ROLE_NAME)).willReturn(false);
            given(teamRoleRepository.saveAndFlush(any(TeamRole.class))).willAnswer(invocation -> {
                TeamRole saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", TEAM_ROLE_ID);
                ReflectionTestUtils.setField(saved, "createdAt", OffsetDateTime.now());
                return saved;
            });

            ResponseTeamRoleDto response =
                    memberService.createTeamRole(OWNER_ID, SPACE_ID, requestOf(ROLE_NAME, COLOR));

            assertThat(response.teamRoleId()).isEqualTo(TEAM_ROLE_ID);
            assertThat(response.roleName()).isEqualTo(ROLE_NAME);
            assertThat(response.color()).isEqualTo(COLOR);
            assertThat(response.createdAt()).isNotNull();

            ArgumentCaptor<TeamRole> captor = ArgumentCaptor.forClass(TeamRole.class);
            verify(teamRoleRepository).saveAndFlush(captor.capture());
            TeamRole saved = captor.getValue();
            assertThat(saved.getTeamId()).isEqualTo(SPACE_ID);
            assertThat(saved.getRoleName()).isEqualTo(ROLE_NAME);
            assertThat(saved.getColor()).isEqualTo(COLOR);
        }

        @Test
        @DisplayName("color 없이 roleName만 요청해도 정상 생성되고 color는 null로 저장된다")
        void createTeamRole_savesWithNullColorWhenOmitted() {
            Team team = teamWithOwner(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(teamRoleRepository.existsByTeamIdAndRoleName(SPACE_ID, ROLE_NAME)).willReturn(false);
            given(teamRoleRepository.saveAndFlush(any(TeamRole.class))).willAnswer(invocation -> {
                TeamRole saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", TEAM_ROLE_ID);
                ReflectionTestUtils.setField(saved, "createdAt", OffsetDateTime.now());
                return saved;
            });

            ResponseTeamRoleDto response =
                    memberService.createTeamRole(OWNER_ID, SPACE_ID, requestOf(ROLE_NAME, null));

            assertThat(response.color()).isNull();
            ArgumentCaptor<TeamRole> captor = ArgumentCaptor.forClass(TeamRole.class);
            verify(teamRoleRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getColor()).isNull();
        }

        @Test
        @DisplayName("존재하지 않거나 삭제된 스페이스면 SPACE_NOT_FOUND 예외가 발생하고 저장을 시도하지 않는다")
        void createTeamRole_throwsWhenSpaceNotFound() {
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    memberService.createTeamRole(OWNER_ID, SPACE_ID, requestOf(ROLE_NAME, COLOR)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_NOT_FOUND);
            verifyNoInteractions(teamRoleRepository);
        }

        @Test
        @DisplayName("요청자가 소유자가 아니면 SPACE_OWNER_ONLY 예외가 발생하고 저장을 시도하지 않는다")
        void createTeamRole_throwsWhenRequesterIsNotOwner() {
            Long nonOwnerId = 99L;
            Team team = teamWithOwner(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));

            assertThatThrownBy(() ->
                    memberService.createTeamRole(nonOwnerId, SPACE_ID, requestOf(ROLE_NAME, COLOR)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_OWNER_ONLY);
            verifyNoInteractions(teamRoleRepository);
        }

        @Test
        @DisplayName("같은 스페이스에 동일한 역할명이 이미 있으면 TEAM_ROLE_NAME_DUPLICATED 예외가 발생하고 저장하지 않는다")
        void createTeamRole_throwsWhenNameAlreadyExists() {
            Team team = teamWithOwner(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(teamRoleRepository.existsByTeamIdAndRoleName(SPACE_ID, ROLE_NAME)).willReturn(true);

            assertThatThrownBy(() ->
                    memberService.createTeamRole(OWNER_ID, SPACE_ID, requestOf(ROLE_NAME, COLOR)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.TEAM_ROLE_NAME_DUPLICATED);
            verify(teamRoleRepository, never()).saveAndFlush(any(TeamRole.class));
        }

        @Test
        @DisplayName("사전 체크는 통과했지만 저장 시 유니크 제약을 위반하면 TEAM_ROLE_NAME_DUPLICATED로 변환된다(동시성 방어)")
        void createTeamRole_translatesUniqueConstraintViolationToDuplicated() {
            Team team = teamWithOwner(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            // 사전 체크 시점엔 중복이 없었지만(false), 동시 요청이 먼저 커밋되어 DB 유니크 제약에 걸리는 상황을 재현한다.
            given(teamRoleRepository.existsByTeamIdAndRoleName(SPACE_ID, ROLE_NAME)).willReturn(false);
            given(teamRoleRepository.saveAndFlush(any(TeamRole.class)))
                    .willThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

            assertThatThrownBy(() ->
                    memberService.createTeamRole(OWNER_ID, SPACE_ID, requestOf(ROLE_NAME, COLOR)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.TEAM_ROLE_NAME_DUPLICATED);
        }
    }
}
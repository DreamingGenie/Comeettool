package com.ssafy.backend.member.service.impl;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.member.dto.RequestChangeAuthorityDto;
import com.ssafy.backend.member.dto.ResponseChangeAuthorityDto;
import com.ssafy.backend.member.entity.Member;
import com.ssafy.backend.member.entity.MemberAuthority;
import com.ssafy.backend.member.repository.MemberRepository;
import com.ssafy.backend.space.entity.Team;
import com.ssafy.backend.space.repository.TeamRepository;
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

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private MemberRepository memberRepository;

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

    @Nested
    @DisplayName("MEMBER-04 멤버 강퇴")
    class KickMember {

        @Test
        @DisplayName("정상 강퇴 시 대상 멤버 레코드가 삭제된다")
        void kickMember_deletesTargetMember() {
            Team team = teamWithOwner(SPACE_ID, OWNER_ID);
            Member member = memberOf(MEMBER_ID, SPACE_ID, TARGET_USER_ID, MemberAuthority.MEMBER);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

            memberService.kickMember(OWNER_ID, SPACE_ID, MEMBER_ID);

            verify(memberRepository).delete(member);
        }

        @Test
        @DisplayName("존재하지 않거나 삭제된 스페이스면 SPACE_NOT_FOUND 예외가 발생하고 멤버 조회를 시도하지 않는다")
        void kickMember_throwsWhenSpaceNotFound() {
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.empty());

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
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));

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
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
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
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
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
}
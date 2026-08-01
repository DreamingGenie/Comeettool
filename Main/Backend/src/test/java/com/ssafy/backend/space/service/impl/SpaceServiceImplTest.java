package com.ssafy.backend.space.service.impl;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.space.dto.RequestCreateSpaceDto;
import com.ssafy.backend.space.dto.RequestTransferOwnerDto;
import com.ssafy.backend.space.dto.RequestUpdateSpaceDto;
import com.ssafy.backend.space.dto.ResponseCreateSpaceDto;
import com.ssafy.backend.space.dto.ResponseSpaceDetailDto;
import com.ssafy.backend.space.dto.ResponseSpaceListDto;
import com.ssafy.backend.space.dto.ResponseTransferOwnerDto;
import com.ssafy.backend.space.dto.ResponseUpdateSpaceDto;
import com.ssafy.backend.member.entity.Member;
import com.ssafy.backend.member.entity.MemberAuthority;
import com.ssafy.backend.space.entity.Team;
import com.ssafy.backend.document.repository.DocumentRepository;
import com.ssafy.backend.meeting.repository.MeetingRoomRepository;
import com.ssafy.backend.member.repository.InvitationRepository;
import com.ssafy.backend.space.mapper.SpaceMapper;
import com.ssafy.backend.member.repository.MemberRepository;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * SpaceServiceImpl 단위 테스트. Repository/Mapper는 전부 Mock — DB 없이 서비스 로직만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpaceServiceImpl 단위 테스트")
class SpaceServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long TEAM_ID = 10L;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private MeetingRoomRepository meetingRoomRepository;

    @Mock
    private InvitationRepository invitationRepository;

    // 실제 매퍼를 주입해 변환 결과까지 검증한다(순수 변환 로직이라 @Spy로 실제 구현 사용).
    @Spy
    private SpaceMapper spaceMapper = new SpaceMapper();

    @InjectMocks
    private SpaceServiceImpl spaceService;

    private User userWithNickname(String nickname, String email) {
        User user = User.builder().email(email).password("enc").build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "nickname", nickname);
        return user;
    }

    private Team teamWithId(Long id, Long ownerId) {
        Team team = Team.builder().name("팀A").description("설명").ownerId(ownerId).color("#123456").build();
        ReflectionTestUtils.setField(team, "id", id);
        return team;
    }

    @Nested
    @DisplayName("SPACE-01 스페이스 생성")
    class AddSpace {

        @Test
        @DisplayName("스페이스를 생성하면 teams가 저장되고 생성자가 Owner 멤버로 등록된다")
        void addSpace_registersCreatorAsOwnerMember() {
            // given
            RequestCreateSpaceDto request = new RequestCreateSpaceDto("팀A", "설명", "#123456", null);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(userWithNickname("진", "u@e.com")));
            given(teamRepository.save(any(Team.class))).willAnswer(inv -> {
                Team t = inv.getArgument(0);
                ReflectionTestUtils.setField(t, "id", TEAM_ID);
                return t;
            });

            // when
            ResponseCreateSpaceDto response = spaceService.addSpace(USER_ID, request);

            // then
            assertThat(response.spaceId()).isEqualTo(TEAM_ID);
            assertThat(response.ownerId()).isEqualTo(USER_ID);
            assertThat(response.teamName()).isEqualTo("팀A");
            assertThat(response.teamColor()).isEqualTo("#123456");

            ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
            verify(memberRepository).save(memberCaptor.capture());
            Member savedMember = memberCaptor.getValue();
            assertThat(savedMember.getUserId()).isEqualTo(USER_ID);
            assertThat(savedMember.getTeamId()).isEqualTo(TEAM_ID);
            assertThat(savedMember.getAuthority()).isEqualTo(MemberAuthority.OWNER);
            assertThat(savedMember.getTeamRoleId()).isNull();
        }

        @Test
        @DisplayName("색상 미지정 시 기본색 #000000이 적용된다")
        void addSpace_appliesDefaultColorWhenBlank() {
            // given
            RequestCreateSpaceDto request = new RequestCreateSpaceDto("팀A", null, null, null);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(userWithNickname("진", "u@e.com")));
            given(teamRepository.save(any(Team.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            ResponseCreateSpaceDto response = spaceService.addSpace(USER_ID, request);

            // then
            assertThat(response.teamColor()).isEqualTo("#000000");
        }

        @Test
        @DisplayName("닉네임이 없으면 이메일 로컬파트를 멤버 닉네임으로 사용한다")
        void addSpace_fallsBackToEmailLocalPartForNickname() {
            // given
            RequestCreateSpaceDto request = new RequestCreateSpaceDto("팀A", null, null, null);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(userWithNickname(null, "hong@example.com")));
            given(teamRepository.save(any(Team.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            spaceService.addSpace(USER_ID, request);

            // then
            ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
            verify(memberRepository).save(memberCaptor.capture());
            assertThat(memberCaptor.getValue().getNickname()).isEqualTo("hong");
        }

        @Test
        @DisplayName("존재하지 않는 사용자면 AUTH_UNAUTHORIZED 예외가 발생하고 저장하지 않는다")
        void addSpace_throwsWhenUserNotFound() {
            // given
            RequestCreateSpaceDto request = new RequestCreateSpaceDto("팀A", null, null, null);
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> spaceService.addSpace(USER_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_UNAUTHORIZED);
            verify(teamRepository, never()).save(any(Team.class));
            verify(memberRepository, never()).save(any(Member.class));
        }
    }

    @Nested
    @DisplayName("SPACE-02 참여 스페이스 목록")
    class FindSpaceList {

        @Test
        @DisplayName("참여 중인 스페이스가 없으면 빈 목록을 반환한다")
        void findSpaceList_returnsEmptyWhenNoneJoined() {
            given(memberRepository.findActiveTeamsWithMyAuthority(USER_ID, null)).willReturn(List.of());

            List<ResponseSpaceListDto> result = spaceService.findSpaceList(USER_ID, null);

            assertThat(result).isEmpty();
            verify(memberRepository, never()).countMembersByTeamIds(anyList());
        }

        @Test
        @DisplayName("참여 스페이스마다 내 역할과 참여자 수를 채워 반환한다")
        void findSpaceList_fillsMyRoleAndMemberCount() {
            // given
            Team team = teamWithId(TEAM_ID, USER_ID);
            given(memberRepository.findActiveTeamsWithMyAuthority(USER_ID, null))
                    .willReturn(List.<Object[]>of(new Object[]{team, MemberAuthority.OWNER}));
            given(memberRepository.countMembersByTeamIds(List.of(TEAM_ID)))
                    .willReturn(List.<Object[]>of(new Object[]{TEAM_ID, 3L}));

            // when
            List<ResponseSpaceListDto> result = spaceService.findSpaceList(USER_ID, null);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).spaceId()).isEqualTo(TEAM_ID);
            assertThat(result.get(0).myAuthority()).isEqualTo("OWNER");
            assertThat(result.get(0).memberCount()).isEqualTo(3L);
        }

        @Test
        @DisplayName("SPACE-03: 공백뿐인 검색어는 null로 정규화해 전체 목록을 조회한다")
        void findSpaceList_blankSearchNormalizedToNull() {
            given(memberRepository.findActiveTeamsWithMyAuthority(USER_ID, null)).willReturn(List.of());

            List<ResponseSpaceListDto> result = spaceService.findSpaceList(USER_ID, "   ");

            assertThat(result).isEmpty();
            verify(memberRepository).findActiveTeamsWithMyAuthority(USER_ID, null);
        }

        @Test
        @DisplayName("SPACE-03: 검색어는 앞뒤 공백을 제거해 이름 필터로 전달한다")
        void findSpaceList_trimsSearchKeyword() {
            given(memberRepository.findActiveTeamsWithMyAuthority(USER_ID, "기획")).willReturn(List.of());

            spaceService.findSpaceList(USER_ID, "  기획  ");

            verify(memberRepository).findActiveTeamsWithMyAuthority(USER_ID, "기획");
        }
    }

    @Nested
    @DisplayName("SPACE-05 스페이스 상세")
    class FindSpaceDetails {

        @Test
        @DisplayName("멤버면 스페이스 정보와 참여자 목록을 반환한다")
        void findSpaceDetails_returnsInfoAndMembersForMember() {
            // given
            Team team = teamWithId(TEAM_ID, USER_ID);
            Member owner = Member.owner(USER_ID, TEAM_ID, "진");
            given(teamRepository.findByIdAndIsDeletedFalse(TEAM_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(TEAM_ID, USER_ID)).willReturn(true);
            given(memberRepository.findByTeamId(TEAM_ID)).willReturn(List.of(owner));

            // when
            ResponseSpaceDetailDto result = spaceService.findSpaceDetails(USER_ID, TEAM_ID);

            // then
            assertThat(result.spaceId()).isEqualTo(TEAM_ID);
            assertThat(result.members()).hasSize(1);
            assertThat(result.members().get(0).authority()).isEqualTo("OWNER");
        }

        @Test
        @DisplayName("존재하지 않거나 삭제된 스페이스면 SPACE_NOT_FOUND 예외가 발생한다")
        void findSpaceDetails_throwsNotFoundWhenAbsent() {
            given(teamRepository.findByIdAndIsDeletedFalse(TEAM_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> spaceService.findSpaceDetails(USER_ID, TEAM_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_NOT_FOUND);
        }

        @Test
        @DisplayName("멤버가 아니면 SPACE_ACCESS_DENIED 예외가 발생하고 참여자를 조회하지 않는다")
        void findSpaceDetails_throwsAccessDeniedForNonMember() {
            Team team = teamWithId(TEAM_ID, 99L);
            given(teamRepository.findByIdAndIsDeletedFalse(TEAM_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(TEAM_ID, USER_ID)).willReturn(false);

            assertThatThrownBy(() -> spaceService.findSpaceDetails(USER_ID, TEAM_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_ACCESS_DENIED);
            verify(memberRepository, never()).findByTeamId(TEAM_ID);
        }
    }

    @Nested
    @DisplayName("SPACE-07 스페이스 나가기")
    class RemoveMyMembership {

        private Member memberOf(Long teamId, Long userId) {
            Member member = Member.builder()
                    .userId(userId).teamId(teamId).authority(MemberAuthority.MEMBER).nickname("진").build();
            return member;
        }

        @Test
        @DisplayName("일반 멤버면 members 행을 삭제한다")
        void removeMyMembership_deletesMemberRow() {
            // given — owner는 다른 사용자(99L), 요청자는 일반 멤버.
            Team team = teamWithId(TEAM_ID, 99L);
            Member member = memberOf(TEAM_ID, USER_ID);
            given(teamRepository.findActiveByIdForUpdate(TEAM_ID)).willReturn(Optional.of(team));
            given(memberRepository.findByTeamIdAndUserId(TEAM_ID, USER_ID)).willReturn(Optional.of(member));

            // when
            spaceService.removeMyMembership(USER_ID, TEAM_ID);

            // then
            verify(memberRepository).delete(member);
        }

        @Test
        @DisplayName("존재하지 않거나 삭제된 스페이스면 SPACE_NOT_FOUND 예외가 발생한다")
        void removeMyMembership_throwsNotFoundWhenAbsent() {
            given(teamRepository.findActiveByIdForUpdate(TEAM_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> spaceService.removeMyMembership(USER_ID, TEAM_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_NOT_FOUND);
            verify(memberRepository, never()).delete(any(Member.class));
        }

        @Test
        @DisplayName("멤버가 아니면 SPACE_ACCESS_DENIED 예외가 발생하고 삭제하지 않는다")
        void removeMyMembership_throwsAccessDeniedForNonMember() {
            Team team = teamWithId(TEAM_ID, 99L);
            given(teamRepository.findActiveByIdForUpdate(TEAM_ID)).willReturn(Optional.of(team));
            given(memberRepository.findByTeamIdAndUserId(TEAM_ID, USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> spaceService.removeMyMembership(USER_ID, TEAM_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_ACCESS_DENIED);
            verify(memberRepository, never()).delete(any(Member.class));
        }

        @Test
        @DisplayName("소유자가 혼자면 SPACE_OWNER_LAST_MEMBER로 삭제를 유도한다(정책 SP-1)")
        void removeMyMembership_throwsLastMemberWhenSoloOwner() {
            // given — 요청자가 team_owner_id이고 멤버가 자신뿐(count=1).
            Team team = teamWithId(TEAM_ID, USER_ID);
            Member owner = memberOf(TEAM_ID, USER_ID);
            given(teamRepository.findActiveByIdForUpdate(TEAM_ID)).willReturn(Optional.of(team));
            given(memberRepository.findByTeamIdAndUserId(TEAM_ID, USER_ID)).willReturn(Optional.of(owner));
            given(memberRepository.countByTeamId(TEAM_ID)).willReturn(1L);

            assertThatThrownBy(() -> spaceService.removeMyMembership(USER_ID, TEAM_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_OWNER_LAST_MEMBER);
            verify(memberRepository, never()).delete(any(Member.class));
        }

        @Test
        @DisplayName("소유자에게 다른 멤버가 있으면 SPACE_OWNER_MUST_TRANSFER로 위임을 유도한다(정책 SP-1)")
        void removeMyMembership_throwsMustTransferWhenOwnerHasOtherMembers() {
            // given — 요청자가 team_owner_id이고 멤버가 2명 이상(count=2).
            Team team = teamWithId(TEAM_ID, USER_ID);
            Member owner = memberOf(TEAM_ID, USER_ID);
            given(teamRepository.findActiveByIdForUpdate(TEAM_ID)).willReturn(Optional.of(team));
            given(memberRepository.findByTeamIdAndUserId(TEAM_ID, USER_ID)).willReturn(Optional.of(owner));
            given(memberRepository.countByTeamId(TEAM_ID)).willReturn(2L);

            assertThatThrownBy(() -> spaceService.removeMyMembership(USER_ID, TEAM_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_OWNER_MUST_TRANSFER);
            verify(memberRepository, never()).delete(any(Member.class));
        }

        @Test
        @DisplayName("GUEST도 소유자가 아니면 정상적으로 나갈 수 있다")
        void removeMyMembership_allowsGuestToLeave() {
            // given — 요청자는 GUEST(비-Owner).
            Team team = teamWithId(TEAM_ID, 99L);
            Member guest = Member.builder()
                    .userId(USER_ID).teamId(TEAM_ID).authority(MemberAuthority.GUEST).nickname("g").build();
            given(teamRepository.findActiveByIdForUpdate(TEAM_ID)).willReturn(Optional.of(team));
            given(memberRepository.findByTeamIdAndUserId(TEAM_ID, USER_ID)).willReturn(Optional.of(guest));

            // when
            spaceService.removeMyMembership(USER_ID, TEAM_ID);

            // then
            verify(memberRepository).delete(guest);
        }
    }

    @Nested
    @DisplayName("SPACE-08 스페이스 정보 수정")
    class ModifySpace {

        @Test
        @DisplayName("소유자면 전달된 필드를 갱신하고 수정 결과를 반환한다")
        void modifySpace_updatesFields() {
            Team team = teamWithId(TEAM_ID, USER_ID);
            given(teamRepository.findActiveByIdForUpdate(TEAM_ID)).willReturn(Optional.of(team));
            RequestUpdateSpaceDto request =
                    new RequestUpdateSpaceDto("새이름", "새설명", "#ABCDEF", "img.png");

            ResponseUpdateSpaceDto result = spaceService.modifySpace(USER_ID, TEAM_ID, request);

            assertThat(team.getName()).isEqualTo("새이름");
            assertThat(team.getDescription()).isEqualTo("새설명");
            assertThat(team.getColor()).isEqualTo("#ABCDEF");
            assertThat(team.getProfileImageUrl()).isEqualTo("img.png");
            assertThat(result.teamName()).isEqualTo("새이름");
            assertThat(result.spaceId()).isEqualTo(TEAM_ID);
        }

        @Test
        @DisplayName("부분 수정: 전달되지 않은(null) 필드는 기존 값을 유지한다")
        void modifySpace_keepsUnspecifiedFields() {
            Team team = teamWithId(TEAM_ID, USER_ID); // name=팀A, description=설명, color=#123456
            given(teamRepository.findActiveByIdForUpdate(TEAM_ID)).willReturn(Optional.of(team));
            RequestUpdateSpaceDto request =
                    new RequestUpdateSpaceDto("새이름", null, null, null);

            spaceService.modifySpace(USER_ID, TEAM_ID, request);

            assertThat(team.getName()).isEqualTo("새이름");
            assertThat(team.getDescription()).isEqualTo("설명");
            assertThat(team.getColor()).isEqualTo("#123456");
        }

        @Test
        @DisplayName("빈 문자열로 설명을 비울 수 있다")
        void modifySpace_clearsDescriptionWithEmptyString() {
            Team team = teamWithId(TEAM_ID, USER_ID);
            given(teamRepository.findActiveByIdForUpdate(TEAM_ID)).willReturn(Optional.of(team));

            spaceService.modifySpace(USER_ID, TEAM_ID, new RequestUpdateSpaceDto(null, "", null, null));

            assertThat(team.getDescription()).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않거나 삭제된 스페이스면 SPACE_NOT_FOUND 예외가 발생한다")
        void modifySpace_throwsNotFoundWhenAbsent() {
            given(teamRepository.findActiveByIdForUpdate(TEAM_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> spaceService.modifySpace(
                    USER_ID, TEAM_ID, new RequestUpdateSpaceDto("새이름", null, null, null)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_NOT_FOUND);
        }

        @Test
        @DisplayName("소유자가 아니면 SPACE_OWNER_ONLY 예외가 발생하고 수정하지 않는다")
        void modifySpace_throwsForNonOwner() {
            Team team = teamWithId(TEAM_ID, 99L);
            given(teamRepository.findActiveByIdForUpdate(TEAM_ID)).willReturn(Optional.of(team));

            assertThatThrownBy(() -> spaceService.modifySpace(
                    USER_ID, TEAM_ID, new RequestUpdateSpaceDto("새이름", null, null, null)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_OWNER_ONLY);
            assertThat(team.getName()).isEqualTo("팀A");
        }

        @Test
        @DisplayName("이름을 공백으로 넘기면 VALIDATION_FAILED 예외가 발생한다(필수 필드는 비울 수 없음)")
        void modifySpace_throwsWhenNameBlank() {
            Team team = teamWithId(TEAM_ID, USER_ID);
            given(teamRepository.findActiveByIdForUpdate(TEAM_ID)).willReturn(Optional.of(team));

            assertThatThrownBy(() -> spaceService.modifySpace(
                    USER_ID, TEAM_ID, new RequestUpdateSpaceDto("   ", null, null, null)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_FAILED);
            assertThat(team.getName()).isEqualTo("팀A");
        }

        @Test
        @DisplayName("색상을 공백으로 넘기면 VALIDATION_FAILED 예외가 발생한다")
        void modifySpace_throwsWhenColorBlank() {
            Team team = teamWithId(TEAM_ID, USER_ID);
            given(teamRepository.findActiveByIdForUpdate(TEAM_ID)).willReturn(Optional.of(team));

            assertThatThrownBy(() -> spaceService.modifySpace(
                    USER_ID, TEAM_ID, new RequestUpdateSpaceDto(null, null, "  ", null)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_FAILED);
        }
    }

    @Nested
    @DisplayName("SPACE-11 스페이스 삭제")
    class RemoveSpace {

        @Test
        @DisplayName("소유자면 teams와 하위 documents·meeting_rooms를 전파 soft delete 하고 관련 초대를 삭제한다")
        void removeSpace_softDeletesTeamAndCascades() {
            // given — 요청자가 team_owner_id.
            Team team = teamWithId(TEAM_ID, USER_ID);
            given(teamRepository.findActiveByIdForUpdate(TEAM_ID)).willReturn(Optional.of(team));

            // when
            spaceService.removeSpace(USER_ID, TEAM_ID);

            // then — teams는 엔티티 상태 변경, 하위는 벌크 갱신 호출. invitations는 is_deleted가 없어 hard delete.
            assertThat(team.isDeleted()).isTrue();
            assertThat(team.getDeletedAt()).isNotNull();
            verify(documentRepository).softDeleteByTeamId(eq(TEAM_ID), any(OffsetDateTime.class));
            verify(meetingRoomRepository).softDeleteByTeamId(eq(TEAM_ID), any(OffsetDateTime.class));
            verify(invitationRepository).deleteByTeamId(TEAM_ID);
        }

        @Test
        @DisplayName("존재하지 않거나 삭제된 스페이스면 SPACE_NOT_FOUND 예외가 발생한다")
        void removeSpace_throwsNotFoundWhenAbsent() {
            given(teamRepository.findActiveByIdForUpdate(TEAM_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> spaceService.removeSpace(USER_ID, TEAM_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_NOT_FOUND);
            verify(documentRepository, never()).softDeleteByTeamId(any(), any());
            verify(meetingRoomRepository, never()).softDeleteByTeamId(any(), any());
            verify(invitationRepository, never()).deleteByTeamId(any());
        }

        @Test
        @DisplayName("소유자가 아니면 SPACE_OWNER_ONLY 예외가 발생하고 삭제하지 않는다")
        void removeSpace_throwsForNonOwner() {
            // given — owner는 다른 사용자(99L).
            Team team = teamWithId(TEAM_ID, 99L);
            given(teamRepository.findActiveByIdForUpdate(TEAM_ID)).willReturn(Optional.of(team));

            assertThatThrownBy(() -> spaceService.removeSpace(USER_ID, TEAM_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_OWNER_ONLY);
            assertThat(team.isDeleted()).isFalse();
            verify(documentRepository, never()).softDeleteByTeamId(any(), any());
            verify(meetingRoomRepository, never()).softDeleteByTeamId(any(), any());
            verify(invitationRepository, never()).deleteByTeamId(any());
        }
    }

    @Nested
    @DisplayName("SPACE-101 소유권 위임")
    class TransferOwner {

        private static final Long NEW_OWNER_ID = 2L;

        private Member memberWithAuthority(Long userId, MemberAuthority authority) {
            return Member.builder()
                    .userId(userId).teamId(TEAM_ID).authority(authority).nickname("m").build();
        }

        @Test
        @DisplayName("소유자가 멤버에게 위임하면 team_owner_id·권한이 갱신된다")
        void transferOwner_promotesTargetAndDemotesOwner() {
            // given — 요청자(USER_ID)가 현재 Owner, 대상(NEW_OWNER_ID)은 일반 멤버.
            Team team = teamWithId(TEAM_ID, USER_ID);
            Member target = memberWithAuthority(NEW_OWNER_ID, MemberAuthority.MEMBER);
            Member currentOwner = memberWithAuthority(USER_ID, MemberAuthority.OWNER);
            given(teamRepository.findActiveByIdForUpdate(TEAM_ID)).willReturn(Optional.of(team));
            given(memberRepository.findByTeamIdAndUserId(TEAM_ID, NEW_OWNER_ID)).willReturn(Optional.of(target));
            given(memberRepository.findByTeamIdAndUserId(TEAM_ID, USER_ID)).willReturn(Optional.of(currentOwner));

            // when
            ResponseTransferOwnerDto response =
                    spaceService.transferOwner(USER_ID, TEAM_ID, new RequestTransferOwnerDto(NEW_OWNER_ID));

            // then
            assertThat(team.getOwnerId()).isEqualTo(NEW_OWNER_ID);
            assertThat(target.getAuthority()).isEqualTo(MemberAuthority.OWNER);
            assertThat(currentOwner.getAuthority()).isEqualTo(MemberAuthority.MEMBER);
            assertThat(response.spaceId()).isEqualTo(TEAM_ID);
            assertThat(response.previousOwnerId()).isEqualTo(USER_ID);
            assertThat(response.newOwnerId()).isEqualTo(NEW_OWNER_ID);
        }

        @Test
        @DisplayName("존재하지 않거나 삭제된 스페이스면 SPACE_NOT_FOUND 예외가 발생한다")
        void transferOwner_throwsNotFoundWhenAbsent() {
            given(teamRepository.findActiveByIdForUpdate(TEAM_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    spaceService.transferOwner(USER_ID, TEAM_ID, new RequestTransferOwnerDto(NEW_OWNER_ID)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_NOT_FOUND);
        }

        @Test
        @DisplayName("요청자가 소유자가 아니면 SPACE_OWNER_ONLY 예외가 발생한다")
        void transferOwner_throwsForNonOwner() {
            Team team = teamWithId(TEAM_ID, 99L);
            given(teamRepository.findActiveByIdForUpdate(TEAM_ID)).willReturn(Optional.of(team));

            assertThatThrownBy(() ->
                    spaceService.transferOwner(USER_ID, TEAM_ID, new RequestTransferOwnerDto(NEW_OWNER_ID)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_OWNER_ONLY);
        }

        @Test
        @DisplayName("대상이 스페이스 멤버가 아니면 SPACE_MEMBER_NOT_FOUND 예외가 발생한다")
        void transferOwner_throwsWhenTargetNotMember() {
            Team team = teamWithId(TEAM_ID, USER_ID);
            given(teamRepository.findActiveByIdForUpdate(TEAM_ID)).willReturn(Optional.of(team));
            given(memberRepository.findByTeamIdAndUserId(TEAM_ID, NEW_OWNER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    spaceService.transferOwner(USER_ID, TEAM_ID, new RequestTransferOwnerDto(NEW_OWNER_ID)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_MEMBER_NOT_FOUND);
        }

        @Test
        @DisplayName("대상이 이미 소유자(자기 자신 포함)면 SPACE_ALREADY_OWNER 예외가 발생한다")
        void transferOwner_throwsWhenTargetAlreadyOwner() {
            // given — 요청자 자신을 대상으로 지정(현재 Owner).
            Team team = teamWithId(TEAM_ID, USER_ID);
            Member ownerSelf = memberWithAuthority(USER_ID, MemberAuthority.OWNER);
            given(teamRepository.findActiveByIdForUpdate(TEAM_ID)).willReturn(Optional.of(team));
            given(memberRepository.findByTeamIdAndUserId(TEAM_ID, USER_ID)).willReturn(Optional.of(ownerSelf));

            assertThatThrownBy(() ->
                    spaceService.transferOwner(USER_ID, TEAM_ID, new RequestTransferOwnerDto(USER_ID)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_ALREADY_OWNER);
        }

        @Test
        @DisplayName("대상이 GUEST면 SPACE_TRANSFER_TARGET_NOT_ELIGIBLE 예외가 발생한다")
        void transferOwner_throwsWhenTargetIsGuest() {
            Team team = teamWithId(TEAM_ID, USER_ID);
            Member guest = memberWithAuthority(NEW_OWNER_ID, MemberAuthority.GUEST);
            given(teamRepository.findActiveByIdForUpdate(TEAM_ID)).willReturn(Optional.of(team));
            given(memberRepository.findByTeamIdAndUserId(TEAM_ID, NEW_OWNER_ID)).willReturn(Optional.of(guest));

            assertThatThrownBy(() ->
                    spaceService.transferOwner(USER_ID, TEAM_ID, new RequestTransferOwnerDto(NEW_OWNER_ID)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_TRANSFER_TARGET_NOT_ELIGIBLE);
        }
    }
}
